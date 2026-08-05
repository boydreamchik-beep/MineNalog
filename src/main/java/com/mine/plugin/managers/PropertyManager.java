package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.utils.TaxUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Управление имуществом: участки, покупка, рассрочка, земельный налог, защита территории.
 *
 * Исправления v6.0.0:
 *  - Координаты участка берутся из ConfigManager (не хардкод)
 *  - Проверка мира во всех обработчиках
 *  - Защита от телепорта (PlayerTeleportEvent)
 *  - dirty-флаг вместо save() на каждое действие
 *  - Просрочка рассрочки навёрстывает пропущенные игровые дни
 *  - Земельный налог напоминается только тем, кто реально просрочил
 *  - Один скан контейнеров вместо двух
 */
public class PropertyManager implements Listener {

    public static final String PLOT_1 = "plot-1";

    private final MinePlugin plugin;
    private final File file;

    private final Map<UUID, List<OwnedPlot>> ownedPlots = new HashMap<>();
    private final Map<UUID, InstallmentData> installments = new HashMap<>();
    private final Map<String, UUID> soldPlots = new HashMap<>();
    private final Map<String, String> soldPlotOwnerNames = new HashMap<>();
    private final Map<UUID, Long> landTaxLastPaid = new HashMap<>();

    private BukkitTask landTaxTask;
    private BukkitTask installmentTask;
    private BukkitTask autoSaveTask;

    private volatile boolean dirty = false;

    public PropertyManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "properties.yml");
    }

    // =====================================================
    // ЗАГРУЗКА / СОХРАНЕНИЕ
    // =====================================================

    public void load() {
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ownedPlots.clear();
        installments.clear();
        soldPlots.clear();
        soldPlotOwnerNames.clear();
        landTaxLastPaid.clear();

        ConfigurationSection soldSec = cfg.getConfigurationSection("sold-plots");
        if (soldSec != null) {
            for (String plotId : soldSec.getKeys(false)) {
                try {
                    String uuidStr = soldSec.getString(plotId + ".owner");
                    if (uuidStr == null) {
                        uuidStr = soldSec.getString(plotId);
                    }
                    if (uuidStr == null) continue;
                    soldPlots.put(plotId, UUID.fromString(uuidStr));
                    String name = soldSec.getString(plotId + ".owner-name", "");
                    if (!name.isEmpty()) soldPlotOwnerNames.put(plotId, name);
                } catch (Exception e) {
                    plugin.getLogger().warning("Битая запись sold-plots: " + plotId);
                }
            }
        }

        ConfigurationSection ownedSec = cfg.getConfigurationSection("owned");
        if (ownedSec != null) {
            for (String uuidStr : ownedSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection plots = ownedSec.getConfigurationSection(uuidStr);
                    if (plots == null) continue;

                    List<OwnedPlot> list = new ArrayList<>();
                    for (String idx : plots.getKeys(false)) {
                        String plotId = plots.getString(idx + ".plot_id", "");
                        if (plotId.isEmpty()) continue;
                        long purchaseDate = plots.getLong(idx + ".purchase_date", 0);
                        boolean paidFull = plots.getBoolean(idx + ".paid_full", true);
                        list.add(new OwnedPlot(plotId, purchaseDate, paidFull));
                    }
                    if (!list.isEmpty()) ownedPlots.put(uuid, list);
                } catch (Exception e) {
                    plugin.getLogger().warning("Битая запись owned: " + uuidStr);
                }
            }
        }

        ConfigurationSection instSec = cfg.getConfigurationSection("installments");
        if (instSec != null) {
            for (String uuidStr : instSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection s = instSec.getConfigurationSection(uuidStr);
                    if (s == null) continue;

                    InstallmentData data = new InstallmentData(
                            s.getString("plot_id", ""),
                            s.getInt("total_cost", 0),
                            s.getInt("remaining", 0),
                            s.getInt("term_days", 0),
                            s.getLong("start_date", System.currentTimeMillis()),
                            s.getLong("due_date", System.currentTimeMillis())
                    );
                    if (data.remaining > 0) installments.put(uuid, data);
                } catch (Exception e) {
                    plugin.getLogger().warning("Битая запись installments: " + uuidStr);
                }
            }
        }

        ConfigurationSection taxSec = cfg.getConfigurationSection("land-tax-last-paid");
        if (taxSec != null) {
            for (String uuidStr : taxSec.getKeys(false)) {
                try {
                    landTaxLastPaid.put(UUID.fromString(uuidStr), taxSec.getLong(uuidStr));
                } catch (Exception e) {
                    plugin.getLogger().warning("Битая запись land-tax-last-paid: " + uuidStr);
                }
            }
        }

        plugin.getLogger().info("Имущество загружено. Участков продано: " + soldPlots.size());
    }

    public void markDirty() {
        dirty = true;
    }

    public void startAutoSave() {
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) saveSync();
        }, 2400L, 2400L);
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) autoSaveTask.cancel();
    }

    public void saveSync() {
        dirty = false;
        YamlConfiguration cfg = new YamlConfiguration();

        for (Map.Entry<String, UUID> entry : soldPlots.entrySet()) {
            String path = "sold-plots." + entry.getKey();
            cfg.set(path + ".owner", entry.getValue().toString());
            String name = soldPlotOwnerNames.get(entry.getKey());
            if (name != null) cfg.set(path + ".owner-name", name);
        }

        for (Map.Entry<UUID, List<OwnedPlot>> entry : ownedPlots.entrySet()) {
            String base = "owned." + entry.getKey();
            int i = 0;
            for (OwnedPlot plot : entry.getValue()) {
                cfg.set(base + "." + i + ".plot_id", plot.plotId);
                cfg.set(base + "." + i + ".purchase_date", plot.purchaseDate);
                cfg.set(base + "." + i + ".paid_full", plot.paidFull);
                i++;
            }
        }

        for (Map.Entry<UUID, InstallmentData> entry : installments.entrySet()) {
            String path = "installments." + entry.getKey();
            InstallmentData d = entry.getValue();
            cfg.set(path + ".plot_id", d.plotId);
            cfg.set(path + ".total_cost", d.totalCost);
            cfg.set(path + ".remaining", d.remaining);
            cfg.set(path + ".term_days", d.termDays);
            cfg.set(path + ".start_date", d.startDate);
            cfg.set(path + ".due_date", d.dueDate);
        }

        for (Map.Entry<UUID, Long> entry : landTaxLastPaid.entrySet()) {
            cfg.set("land-tax-last-paid." + entry.getKey(), entry.getValue());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить properties.yml", e);
        }
    }

    // =====================================================
    // ГРАНИЦЫ УЧАСТКА (из конфига)
    // =====================================================

    public boolean isInsidePlot1(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        ConfigManager cfg = plugin.getConfigManager();
        if (!loc.getWorld().getName().equals(cfg.getPlot1World())) return false;

        double minX = Math.min(cfg.getPlot1MinX(), cfg.getPlot1MaxX());
        double maxX = Math.max(cfg.getPlot1MinX(), cfg.getPlot1MaxX());
        double minY = Math.min(cfg.getPlot1MinY(), cfg.getPlot1MaxY());
        double maxY = Math.max(cfg.getPlot1MinY(), cfg.getPlot1MaxY());
        double minZ = Math.min(cfg.getPlot1MinZ(), cfg.getPlot1MaxZ());
        double maxZ = Math.max(cfg.getPlot1MinZ(), cfg.getPlot1MaxZ());

        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean isPlot1Owner(UUID uuid) {
        UUID owner = soldPlots.get(PLOT_1);
        return owner != null && owner.equals(uuid);
    }

    public String getPlotCoordinates(String plotId) {
        if (!PLOT_1.equals(plotId)) return "Неизвестно";
        ConfigManager cfg = plugin.getConfigManager();
        return String.format("от (%.1f, %.1f, %.1f) до (%.1f, %.1f, %.1f)",
                cfg.getPlot1MinX(), cfg.getPlot1MinY(), cfg.getPlot1MinZ(),
                cfg.getPlot1MaxX(), cfg.getPlot1MaxY(), cfg.getPlot1MaxZ());
    }

    public int getPlotPrice(String plotId) {
        if (!PLOT_1.equals(plotId)) return Integer.MAX_VALUE;
        ConfigManager cfg = plugin.getConfigManager();
        return cfg.getPlot1PricePerBlock() * cfg.getPlot1SurfaceBlocks();
    }

    // =====================================================
    // ЗАЩИТА УЧАСТКА
    // =====================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isInsidePlot1(event.getBlock().getLocation())) return;
        if (isPlot1Owner(event.getPlayer().getUniqueId())) return;
        if (event.getPlayer().hasPermission("mine.admin")) return;

        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text("Частная территория — ломать нельзя")
                .color(NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isInsidePlot1(event.getBlock().getLocation())) return;
        if (isPlot1Owner(event.getPlayer().getUniqueId())) return;
        if (event.getPlayer().hasPermission("mine.admin")) return;

        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text("Частная территория — строить нельзя")
                .color(NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        if (!soldPlots.containsKey(PLOT_1)) return;

        Player player = event.getPlayer();
        if (isPlot1Owner(player.getUniqueId())) return;
        if (player.hasPermission("mine.admin")) return;

        if (isInsidePlot1(to) && !isInsidePlot1(from)) {
            event.setTo(from);
            player.sendActionBar(Component.text("Частная территория — вход запрещён")
                    .color(NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        if (!soldPlots.containsKey(PLOT_1)) return;

        Player player = event.getPlayer();
        if (isPlot1Owner(player.getUniqueId())) return;
        if (player.hasPermission("mine.admin")) return;

        if (isInsidePlot1(to)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("[Участок] Телепорт на частную территорию запрещён!")
                    .color(NamedTextColor.RED));
        }
    }

    // =====================================================
    // ПОКУПКА
    // =====================================================

    public boolean isPlotSold(String plotId) {
        return soldPlots.containsKey(plotId);
    }

    public UUID getPlotOwner(String plotId) {
        return soldPlots.get(plotId);
    }

    public String getPlotOwnerName(String plotId) {
        String cached = soldPlotOwnerNames.get(plotId);
        if (cached != null && !cached.isEmpty()) return cached;

        UUID uuid = soldPlots.get(plotId);
        if (uuid == null) return "Неизвестно";

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();

        return "Неизвестно";
    }

    /**
     * Полная покупка участка. Списывает булыжник из инвентаря и контейнеров.
     */
    public boolean buyPlotFull(UUID uuid, String plotId, int cost, Player player) {
        if (isPlotSold(plotId)) return false;
        if (cost <= 0) return false;

        Material currency = plugin.getConfigManager().getShopCurrency();

        int available = ChestScanner.countTotalMaterial(player, currency);
        if (available < cost) return false;

        int notRemoved = ChestScanner.removeMaterialFromAll(player, currency, cost);
        int actuallyPaid = cost - notRemoved;

        if (actuallyPaid < cost) {
            // Откат: возвращаем то, что успели снять
            if (actuallyPaid > 0) {
                TaxUtils.giveItemOrDrop(player,
                        new org.bukkit.inventory.ItemStack(currency, actuallyPaid));
            }
            return false;
        }

        List<OwnedPlot> plots = ownedPlots.computeIfAbsent(uuid, k -> new ArrayList<>());
        plots.add(new OwnedPlot(plotId, System.currentTimeMillis(), true));

        soldPlots.put(plotId, uuid);
        soldPlotOwnerNames.put(plotId, player.getName());
        landTaxLastPaid.put(uuid, System.currentTimeMillis());

        plugin.getKaznaManager().addItem(currency, cost);

        markDirty();
        return true;
    }

    // =====================================================
    // РАССРОЧКА
    // =====================================================

    public InstallmentResult evaluateInstallment(UUID uuid, String plotId, int cost, Player player) {
        if (isPlotSold(plotId)) {
            return new InstallmentResult(false, 0, "Участок уже продан");
        }
        if (cost <= 0) {
            return new InstallmentResult(false, 0, "Некорректная стоимость");
        }

        ConfigManager cfg = plugin.getConfigManager();
        int minIncome = cfg.getInstallmentMinIncome();
        double termMultiplier = cfg.getInstallmentTermMultiplier();

        int income = plugin.getIncomeTracker().calculateIncome(uuid);
        int cobble = ChestScanner.countTotalMaterial(player, cfg.getShopCurrency());
        int totalIncome = income + cobble;

        if (totalIncome < minIncome) {
            return new InstallmentResult(false, 0,
                    "Недостаточный доход: " + totalIncome + " (мин: " + minIncome + ")");
        }

        int termDays = Math.max(1, (int) (((double) totalIncome / cost) * termMultiplier));
        termDays = Math.min(termDays, cfg.getInstallmentMaxTermDays());

        return new InstallmentResult(true, termDays,
                "Одобрено на " + termDays + " игр. дней. Доход: " + totalIncome);
    }

    public void createInstallment(UUID uuid, String plotId, int cost, int termDays) {
        if (isPlotSold(plotId)) return;

        long now = System.currentTimeMillis();
        long gameDayMs = plugin.getConfigManager().getGameDayMillis();
        long dueDate = now + ((long) termDays * gameDayMs);

        installments.put(uuid, new InstallmentData(plotId, cost, cost, termDays, now, dueDate));

        List<OwnedPlot> plots = ownedPlots.computeIfAbsent(uuid, k -> new ArrayList<>());
        plots.add(new OwnedPlot(plotId, now, false));

        soldPlots.put(plotId, uuid);
        Player p = Bukkit.getPlayer(uuid);
        soldPlotOwnerNames.put(plotId, p != null ? p.getName() : "Неизвестно");
        landTaxLastPaid.put(uuid, now);

        markDirty();
    }

    public PayInstallmentResult payInstallment(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        InstallmentData data = installments.get(uuid);

        if (data == null) return PayInstallmentResult.NO_INSTALLMENT;
        if (amount <= 0) return PayInstallmentResult.INVALID_AMOUNT;

        Material currency = plugin.getConfigManager().getShopCurrency();
        int actualPay = Math.min(amount, data.remaining);

        int available = ChestScanner.countTotalMaterial(player, currency);
        if (available < actualPay) return PayInstallmentResult.NOT_ENOUGH;

        int notRemoved = ChestScanner.removeMaterialFromAll(player, currency, actualPay);
        int paid = actualPay - notRemoved;
        if (paid <= 0) return PayInstallmentResult.NOT_ENOUGH;

        plugin.getKaznaManager().addItem(currency, paid);
        data.remaining -= paid;

        if (data.remaining <= 0) {
            installments.remove(uuid);
            List<OwnedPlot> plots = ownedPlots.get(uuid);
            if (plots != null) {
                for (OwnedPlot plot : plots) {
                    if (plot.plotId.equals(data.plotId)) plot.paidFull = true;
                }
            }
        }

        markDirty();
        return PayInstallmentResult.SUCCESS;
    }

    /**
     * Проверка просрочки. Навёрстывает пропущенные игровые дни.
     */
    public void startInstallmentChecker() {
        installmentTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ConfigManager cfg = plugin.getConfigManager();
            double overdueRate = cfg.getInstallmentOverdueRate();
            long gameDayMs = cfg.getGameDayMillis();
            long now = System.currentTimeMillis();

            boolean changed = false;

            for (Map.Entry<UUID, InstallmentData> entry : installments.entrySet()) {
                InstallmentData data = entry.getValue();
                if (data.remaining <= 0) continue;

                int totalInterest = 0;
                int guard = 0;

                while (now > data.dueDate && guard < 1000) {
                    int interest = (int) Math.ceil(data.remaining * overdueRate);
                    data.remaining += interest;
                    totalInterest += interest;
                    data.dueDate += gameDayMs;
                    guard++;
                    changed = true;
                }

                if (totalInterest > 0) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("╔══════════════════════════════╗")
                                .color(NamedTextColor.RED));
                        player.sendMessage(Component.text("║  ПРОСРОЧКА РАССРОЧКИ!")
                                .color(NamedTextColor.RED)
                                .decoration(TextDecoration.BOLD, true));
                        player.sendMessage(Component.text("║  Начислено: +" + totalInterest)
                                .color(NamedTextColor.YELLOW));
                        player.sendMessage(Component.text("║  Остаток долга: " + data.remaining)
                                .color(NamedTextColor.WHITE));
                        player.sendMessage(Component.text("║  /property pay <сумма>")
                                .color(NamedTextColor.GREEN));
                        player.sendMessage(Component.text("╚══════════════════════════════╝")
                                .color(NamedTextColor.RED));
                        player.sendMessage(Component.empty());
                    }
                }
            }

            if (changed) markDirty();
        }, 1200L, 6000L); // первая проверка через минуту, далее каждые 5 минут
    }

    // =====================================================
    // ЗЕМЕЛЬНЫЙ НАЛОГ
    // =====================================================

    public void startLandTaxReminder() {
        landTaxTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ConfigManager cfg = plugin.getConfigManager();
            long intervalMs = (long) cfg.getLandTaxIntervalGameDays() * cfg.getGameDayMillis();
            long now = System.currentTimeMillis();
            int amount = cfg.getLandTaxAmount();

            for (Map.Entry<String, UUID> entry : soldPlots.entrySet()) {
                UUID ownerUuid = entry.getValue();

                long last = landTaxLastPaid.getOrDefault(ownerUuid, 0L);
                if (now - last < intervalMs) continue;

                Player player = Bukkit.getPlayer(ownerUuid);
                if (player == null || !player.isOnline()) continue;

                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("╔══════════════════════════════╗")
                        .color(NamedTextColor.RED));
                player.sendMessage(Component.text("║  ЗЕМЕЛЬНЫЙ НАЛОГ!")
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true));
                player.sendMessage(Component.text("║  Оплатите: " + amount + " булыжников")
                        .color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("║  (" + (amount / 64) + " стака)")
                        .color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("║  Команда: /property tax")
                        .color(NamedTextColor.GREEN));
                player.sendMessage(Component.text("╚══════════════════════════════╝")
                        .color(NamedTextColor.RED));
                player.sendMessage(Component.empty());
            }
        }, 2400L, 6000L); // первое напоминание через 2 минуты, далее каждые 5 минут
    }

    public void stopTasks() {
        if (landTaxTask != null) landTaxTask.cancel();
        if (installmentTask != null) installmentTask.cancel();
    }

    public boolean payLandTax(Player player) {
        UUID uuid = player.getUniqueId();
        if (!hasAnyPlot(uuid)) return false;

        ConfigManager cfg = plugin.getConfigManager();
        Material currency = cfg.getShopCurrency();
        int amount = cfg.getLandTaxAmount();

        int available = ChestScanner.countTotalMaterial(player, currency);
        if (available < amount) return false;

        int notRemoved = ChestScanner.removeMaterialFromAll(player, currency, amount);
        int paid = amount - notRemoved;

        if (paid < amount) {
            if (paid > 0) {
                TaxUtils.giveItemOrDrop(player,
                        new org.bukkit.inventory.ItemStack(currency, paid));
            }
            return false;
        }

        plugin.getKaznaManager().addItem(currency, amount);
        plugin.getIncomeTracker().recordAutoTax(uuid, amount);

        landTaxLastPaid.put(uuid, System.currentTimeMillis());
        markDirty();
        return true;
    }

    public boolean isLandTaxDue(UUID uuid) {
        if (!hasAnyPlot(uuid)) return false;
        ConfigManager cfg = plugin.getConfigManager();
        long intervalMs = (long) cfg.getLandTaxIntervalGameDays() * cfg.getGameDayMillis();
        long last = landTaxLastPaid.getOrDefault(uuid, 0L);
        return System.currentTimeMillis() - last >= intervalMs;
    }

    public long getLandTaxNextDue(UUID uuid) {
        ConfigManager cfg = plugin.getConfigManager();
        long intervalMs = (long) cfg.getLandTaxIntervalGameDays() * cfg.getGameDayMillis();
        long last = landTaxLastPaid.getOrDefault(uuid, 0L);
        return last + intervalMs;
    }

    // =====================================================
    // ГЕТТЕРЫ
    // =====================================================

    public boolean hasAnyPlot(UUID uuid) {
        List<OwnedPlot> plots = ownedPlots.get(uuid);
        return plots != null && !plots.isEmpty();
    }

    public boolean hasPlot(UUID uuid, String plotId) {
        List<OwnedPlot> plots = ownedPlots.get(uuid);
        if (plots == null) return false;
        return plots.stream().anyMatch(p -> p.plotId.equals(plotId));
    }

    public boolean hasAnyInstallment(UUID uuid) {
        return installments.containsKey(uuid);
    }

    public InstallmentData getInstallment(UUID uuid) {
        return installments.get(uuid);
    }

    public List<OwnedPlot> getOwnedPlots(UUID uuid) {
        return ownedPlots.getOrDefault(uuid, new ArrayList<>());
    }

    // =====================================================
    // КЛАССЫ ДАННЫХ
    // =====================================================

    public static class OwnedPlot {
        public final String plotId;
        public final long purchaseDate;
        public boolean paidFull;

        public OwnedPlot(String plotId, long purchaseDate, boolean paidFull) {
            this.plotId = plotId;
            this.purchaseDate = purchaseDate;
            this.paidFull = paidFull;
        }
    }

    public static class InstallmentData {
        public String plotId;
        public int totalCost;
        public int remaining;
        public int termDays;
        public long startDate;
        public long dueDate;

        public InstallmentData(String plotId, int totalCost, int remaining,
                               int termDays, long startDate, long dueDate) {
            this.plotId = plotId;
            this.totalCost = totalCost;
            this.remaining = remaining;
            this.termDays = termDays;
            this.startDate = startDate;
            this.dueDate = dueDate;
        }
    }

    public static class InstallmentResult {
        public final boolean approved;
        public final int termDays;
        public final String message;

        public InstallmentResult(boolean approved, int termDays, String message) {
            this.approved = approved;
            this.termDays = termDays;
            this.message = message;
        }
    }

    public enum PayInstallmentResult {
        SUCCESS,
        NO_INSTALLMENT,
        INVALID_AMOUNT,
        NOT_ENOUGH
    }
}
