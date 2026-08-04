package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PropertyManager implements Listener {

    private final MinePlugin plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<UUID, List<OwnedPlot>> ownedPlots = new HashMap<>();
    private final Map<UUID, InstallmentData> installments = new HashMap<>();
    private final Map<String, UUID> soldPlots = new HashMap<>();

    // Земельный налог: UUID -> последняя оплата (timestamp)
    private final Map<UUID, Long> landTaxPaid = new HashMap<>();

    private BukkitTask landTaxTask;

    // Координаты участка №1
    public static final double PLOT1_MIN_X = -239.300;
    public static final double PLOT1_MIN_Y = 64.0;
    public static final double PLOT1_MIN_Z = -63.525;
    public static final double PLOT1_MAX_X = -229.458;
    public static final double PLOT1_MAX_Y = 87.0;
    public static final double PLOT1_MAX_Z = -43.642;

    // Земельный налог: 2 стака = 128 булыжников
    public static final int LAND_TAX_AMOUNT = 128;

    public PropertyManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "properties.yml");
    }

    public void load() {
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        config = YamlConfiguration.loadConfiguration(file);
        ownedPlots.clear();
        installments.clear();
        soldPlots.clear();
        landTaxPaid.clear();

        // Загрузка проданных
        if (config.contains("sold-plots")) {
            var section = config.getConfigurationSection("sold-plots");
            if (section != null) {
                for (String plotId : section.getKeys(false)) {
                    try {
                        String uuidStr = section.getString(plotId);
                        if (uuidStr != null) {
                            soldPlots.put(plotId, UUID.fromString(uuidStr));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Загрузка собственности
        if (config.contains("owned")) {
            var section = config.getConfigurationSection("owned");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        var plotSection = section.getConfigurationSection(uuidStr);
                        if (plotSection != null) {
                            List<OwnedPlot> plots = new ArrayList<>();
                            for (String plotKey : plotSection.getKeys(false)) {
                                String plotId = plotSection.getString(plotKey + ".plot_id", "");
                                long purchaseDate = plotSection.getLong(plotKey + ".purchase_date", 0);
                                boolean paidFull = plotSection.getBoolean(plotKey + ".paid_full", true);
                                plots.add(new OwnedPlot(plotId, purchaseDate, paidFull));
                            }
                            ownedPlots.put(uuid, plots);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Загрузка рассрочек
        if (config.contains("installments")) {
            var section = config.getConfigurationSection("installments");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        var s = section.getConfigurationSection(uuidStr);
                        if (s != null) {
                            InstallmentData data = new InstallmentData(
                                    s.getString("plot_id", ""),
                                    s.getInt("total_cost", 0),
                                    s.getInt("remaining", 0),
                                    s.getInt("term_days", 0),
                                    s.getLong("start_date", 0),
                                    s.getLong("due_date", 0)
                            );
                            installments.put(uuid, data);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Загрузка земельного налога
        if (config.contains("land-tax-paid")) {
            var section = config.getConfigurationSection("land-tax-paid");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        long time = section.getLong(uuidStr);
                        landTaxPaid.put(uuid, time);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public void save() {
        config = new YamlConfiguration();

        for (var entry : soldPlots.entrySet()) {
            config.set("sold-plots." + entry.getKey(), entry.getValue().toString());
        }

        for (var entry : ownedPlots.entrySet()) {
            String basePath = "owned." + entry.getKey().toString();
            int i = 0;
            for (OwnedPlot plot : entry.getValue()) {
                config.set(basePath + "." + i + ".plot_id", plot.plotId);
                config.set(basePath + "." + i + ".purchase_date", plot.purchaseDate);
                config.set(basePath + "." + i + ".paid_full", plot.paidFull);
                i++;
            }
        }

        for (var entry : installments.entrySet()) {
            String path = "installments." + entry.getKey().toString();
            InstallmentData d = entry.getValue();
            config.set(path + ".plot_id", d.plotId);
            config.set(path + ".total_cost", d.totalCost);
            config.set(path + ".remaining", d.remaining);
            config.set(path + ".term_days", d.termDays);
            config.set(path + ".start_date", d.startDate);
            config.set(path + ".due_date", d.dueDate);
        }

        for (var entry : landTaxPaid.entrySet()) {
            config.set("land-tax-paid." + entry.getKey().toString(), entry.getValue());
        }

        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    // === ЗЕМЕЛЬНЫЙ НАЛОГ ===

    public void startLandTaxReminder() {
        // Каждые 3 игровых дня = 3 * 24000 тиков
        long interval = 3L * 24000L;
        landTaxTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (var entry : soldPlots.entrySet()) {
                UUID ownerUuid = entry.getValue();
                Player player = Bukkit.getPlayer(ownerUuid);
                if (player != null && player.isOnline()) {
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("╔══════════════════════════════╗")
                            .color(NamedTextColor.RED));
                    player.sendMessage(Component.text("║  ЗЕМЕЛЬНЫЙ НАЛОГ!")
                            .color(NamedTextColor.RED)
                            .decoration(TextDecoration.BOLD, true));
                    player.sendMessage(Component.text("║  Оплатите: " + LAND_TAX_AMOUNT + " булыжников")
                            .color(NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("║  (2 стака)")
                            .color(NamedTextColor.GRAY));
                    player.sendMessage(Component.text("║  Команда: /property tax")
                            .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.text("╚══════════════════════════════╝")
                            .color(NamedTextColor.RED));
                    player.sendMessage(Component.empty());
                }
            }
        }, interval, interval);
    }

    public void stopLandTaxReminder() {
        if (landTaxTask != null) landTaxTask.cancel();
    }

    /**
     * Оплатить земельный налог
     */
    public boolean payLandTax(Player player) {
        UUID uuid = player.getUniqueId();

        // Проверяем есть ли земля
        if (!hasAnyPlot(uuid)) return false;

        int cobble = ChestScanner.countTotalMaterial(player, Material.COBBLESTONE);
        if (cobble < LAND_TAX_AMOUNT) return false;

        ChestScanner.removeMaterialFromAll(player, Material.COBBLESTONE, LAND_TAX_AMOUNT);
        plugin.getKaznaManager().addItem(Material.COBBLESTONE, LAND_TAX_AMOUNT);
        plugin.getIncomeTracker().recordAutoTax(uuid, LAND_TAX_AMOUNT);

        landTaxPaid.put(uuid, System.currentTimeMillis());
        save();
        return true;
    }

    public boolean hasAnyPlot(UUID uuid) {
        List<OwnedPlot> plots = ownedPlots.get(uuid);
        return plots != null && !plots.isEmpty();
    }

    // === УЧАСТОК: ЗАЩИТА ===

    /**
     * Проверяет находится ли локация внутри участка №1
     */
    public boolean isInsidePlot1(Location loc) {
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        double minX = Math.min(PLOT1_MIN_X, PLOT1_MAX_X);
        double maxX = Math.max(PLOT1_MIN_X, PLOT1_MAX_X);
        double minY = Math.min(PLOT1_MIN_Y, PLOT1_MAX_Y);
        double maxY = Math.max(PLOT1_MIN_Y, PLOT1_MAX_Y);
        double minZ = Math.min(PLOT1_MIN_Z, PLOT1_MAX_Z);
        double maxZ = Math.max(PLOT1_MIN_Z, PLOT1_MAX_Z);

        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * Проверяет является ли игрок владельцем участка №1
     */
    public boolean isPlot1Owner(UUID uuid) {
        UUID owner = soldPlots.get("plot-1");
        return owner != null && owner.equals(uuid);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isInsidePlot1(event.getBlock().getLocation())) {
            if (!isPlot1Owner(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("[Участок] Это частная территория! Нельзя ломать.")
                        .color(NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isInsidePlot1(event.getBlock().getLocation())) {
            if (!isPlot1Owner(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("[Участок] Это частная территория! Нельзя строить.")
                        .color(NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        // Проверяем вход на участок
        if (isInsidePlot1(to) && !isInsidePlot1(from)) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            if (!isPlot1Owner(uuid)) {
                // Не пускаем
                event.setTo(from);
                player.sendMessage(Component.text("[Участок] Это частная территория! Вход запрещён.")
                        .color(NamedTextColor.RED));
            }
        }
    }

    // === ПОКУПКА ===

    public boolean isPlotSold(String plotId) {
        return soldPlots.containsKey(plotId);
    }

    public UUID getPlotOwner(String plotId) {
        return soldPlots.get(plotId);
    }

    public boolean buyPlotFull(UUID uuid, String plotId, int cost, Player player) {
        if (isPlotSold(plotId)) return false;

        int cobbleInInventory = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COBBLESTONE) {
                cobbleInInventory += item.getAmount();
            }
        }

        int cobbleInContainers = ChestScanner.countInNearbyContainers(player, Material.COBBLESTONE);
        int total = cobbleInInventory + cobbleInContainers;

        if (total < cost) return false;

        ChestScanner.removeMaterialFromAll(player, Material.COBBLESTONE, cost);

        List<OwnedPlot> plots = ownedPlots.computeIfAbsent(uuid, k -> new ArrayList<>());
        plots.add(new OwnedPlot(plotId, System.currentTimeMillis(), true));

        soldPlots.put(plotId, uuid);
        plugin.getKaznaManager().addItem(Material.COBBLESTONE, cost);

        save();
        return true;
    }

    // Нужен импорт
    private static org.bukkit.inventory.ItemStack dummyImport;

    // === РАССРОЧКА ===

    public InstallmentResult evaluateInstallment(UUID uuid, String plotId, int cost, Player player) {
        if (isPlotSold(plotId)) {
            return new InstallmentResult(false, 0, "Участок уже продан");
        }

        int minIncome = plugin.getConfig().getInt("property.installment.min-income", 500);
        double termMultiplier = plugin.getConfig().getDouble("property.installment.term-multiplier", 5);

        // Доход = добытое в шахте
        int income = plugin.getIncomeTracker().calculateIncome(uuid);

        // Булыжник во всех сундуках и инвентаре
        int cobbleInInventory = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COBBLESTONE) {
                cobbleInInventory += item.getAmount();
            }
        }
        int cobbleInContainers = ChestScanner.countInNearbyContainers(player, Material.COBBLESTONE);
        int totalCobble = cobbleInInventory + cobbleInContainers;

        int totalIncome = income + totalCobble;

        if (totalIncome < minIncome) {
            return new InstallmentResult(false, 0, "Недостаточный доход: " + totalIncome
                    + " (мин: " + minIncome + ")");
        }

        // Индивидуальный срок
        int termDays = Math.max(1, (int) (((double) totalIncome / cost) * termMultiplier));
        termDays = Math.min(termDays, 30);

        return new InstallmentResult(true, termDays,
                "Одобрено на " + termDays + " игр. дней. Доход: " + totalIncome);
    }

    public void createInstallment(UUID uuid, String plotId, int cost, int termDays) {
        if (isPlotSold(plotId)) return;

        long now = System.currentTimeMillis();
        // 1 игровой день = 20 минут реального времени
        long dueDate = now + ((long) termDays * 20 * 60 * 1000);

        installments.put(uuid, new InstallmentData(plotId, cost, cost, termDays, now, dueDate));

        List<OwnedPlot> plots = ownedPlots.computeIfAbsent(uuid, k -> new ArrayList<>());
        plots.add(new OwnedPlot(plotId, now, false));

        soldPlots.put(plotId, uuid);
        save();
    }

    /**
     * Оплатить рассрочку
     */
    public PayInstallmentResult payInstallment(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        InstallmentData data = installments.get(uuid);

        if (data == null) return PayInstallmentResult.NO_INSTALLMENT;
        if (amount <= 0) return PayInstallmentResult.INVALID_AMOUNT;

        int actualPay = Math.min(amount, data.remaining);

        // Считаем булыжник
        int cobbleInInventory = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COBBLESTONE) {
                cobbleInInventory += item.getAmount();
            }
        }
        int cobbleInContainers = ChestScanner.countInNearbyContainers(player, Material.COBBLESTONE);
        int totalCobble = cobbleInInventory + cobbleInContainers;

        if (totalCobble < actualPay) return PayInstallmentResult.NOT_ENOUGH;

        ChestScanner.removeMaterialFromAll(player, Material.COBBLESTONE, actualPay);
        plugin.getKaznaManager().addItem(Material.COBBLESTONE, actualPay);

        data.remaining -= actualPay;

        if (data.remaining <= 0) {
            // Рассрочка полностью оплачена
            installments.remove(uuid);

            // Отмечаем участок как оплаченный
            List<OwnedPlot> plots = ownedPlots.get(uuid);
            if (plots != null) {
                for (OwnedPlot plot : plots) {
                    if (plot.plotId.equals(data.plotId)) {
                        plot.paidFull = true;
                    }
                }
            }
        }

        save();
        return PayInstallmentResult.SUCCESS;
    }

    /**
     * Начисление процентов за просроченные рассрочки
     */
    public void startInstallmentChecker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            double overdueRate = plugin.getConfig().getDouble(
                    "property.installment.overdue-rate", 0.03);

            for (var entry : installments.entrySet()) {
                InstallmentData data = entry.getValue();

                if (now > data.dueDate && data.remaining > 0) {
                    int interest = (int) Math.ceil(data.remaining * overdueRate);
                    data.remaining += interest;

                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("╔══════════════════════════════╗")
                                .color(NamedTextColor.RED));
                        player.sendMessage(Component.text("║  ПРОСРОЧКА РАССРОЧКИ!")
                                .color(NamedTextColor.RED)
                                .decoration(TextDecoration.BOLD, true));
                        player.sendMessage(Component.text("║  Начислены проценты: +" + interest)
                                .color(NamedTextColor.YELLOW));
                        player.sendMessage(Component.text("║  Остаток долга: " + data.remaining)
                                .color(NamedTextColor.WHITE));
                        player.sendMessage(Component.text("║  /property pay <сумма>")
                                .color(NamedTextColor.GREEN));
                        player.sendMessage(Component.text("╚══════════════════════════════╝")
                                .color(NamedTextColor.RED));
                    }

                    save();
                }
            }
        }, 24000L, 24000L); // Каждый игровой день
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

    /**
     * Получить координаты участка как строку
     */
    public String getPlotCoordinates(String plotId) {
        if (plotId.equals("plot-1")) {
            return "от (" + (int)PLOT1_MIN_X + ", " + (int)PLOT1_MIN_Y + ", " + (int)PLOT1_MIN_Z
                    + ") до (" + (int)PLOT1_MAX_X + ", " + (int)PLOT1_MAX_Y + ", " + (int)PLOT1_MAX_Z + ")";
        }
        return "Неизвестно";
    }

    // === Классы ===

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
