package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Управление имуществом.
 * 
 * ИЗМЕНЕНИЯ:
 * - Отменяются все АКТИВНЫЕ рассрочки при загрузке (одноразово)
 * - Каждый участок можно купить только ОДИН РАЗ (глобально)
 * - Флаг soldTo = кто купил (UUID) или null если свободен
 */
public class PropertyManager {

    private final MinePlugin plugin;
    private final File file;
    private FileConfiguration config;

    // UUID владельца -> список участков
    private final Map<UUID, List<OwnedPlot>> ownedPlots = new HashMap<>();

    // UUID -> рассрочка (только новые, старые отменяются)
    private final Map<UUID, InstallmentData> installments = new HashMap<>();

    // plotId -> UUID покупателя (если продан)
    private final Map<String, UUID> soldPlots = new HashMap<>();

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

        // Загрузка проданных участков
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

        // Загрузка участков в собственности
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

        // ОТМЕНА всех активных рассрочек (одноразово)
        // Сохранённые рассрочки НЕ загружаем — они отменяются
        // При этом участки в собственности остаются с флагом paidFull = true
        cancelAllActiveInstallments();

        save();
        plugin.getLogger().info("Загружено участков: " + ownedPlots.size()
                + ", продано: " + soldPlots.size());
    }

    /**
     * Отменяет все активные рассрочки.
     * Если у игрока была рассрочка — участок остаётся у него как оплаченный полностью.
     */
    private void cancelAllActiveInstallments() {
        // Проходим по всем участкам, у кого не был paid_full = true
        for (var entry : ownedPlots.entrySet()) {
            for (OwnedPlot plot : entry.getValue()) {
                if (!plot.paidFull) {
                    plot.paidFull = true;
                    plugin.getLogger().info("Рассрочка отменена для игрока "
                            + entry.getKey() + " на участок " + plot.plotId);
                }
            }
        }
        installments.clear();
    }

    public void save() {
        config = new YamlConfiguration();

        // Проданные
        for (var entry : soldPlots.entrySet()) {
            config.set("sold-plots." + entry.getKey(), entry.getValue().toString());
        }

        // Собственность
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

        // Рассрочки (новые)
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

        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Проверить продан ли участок
     */
    public boolean isPlotSold(String plotId) {
        return soldPlots.containsKey(plotId);
    }

    /**
     * Получить UUID владельца участка
     */
    public UUID getPlotOwner(String plotId) {
        return soldPlots.get(plotId);
    }

    /**
     * Купить участок за полную стоимость
     */
    public boolean buyPlotFull(UUID uuid, String plotId, int cost, org.bukkit.entity.Player player) {
        // Проверка: продан ли
        if (isPlotSold(plotId)) return false;

        int total = ChestScanner.countTotalMaterial(player, Material.COBBLESTONE);
        if (total < cost) return false;

        ChestScanner.removeMaterialFromAll(player, Material.COBBLESTONE, cost);

        List<OwnedPlot> plots = ownedPlots.computeIfAbsent(uuid, k -> new ArrayList<>());
        plots.add(new OwnedPlot(plotId, System.currentTimeMillis(), true));

        // Отмечаем как проданный
        soldPlots.put(plotId, uuid);

        // Добавляем в казну
        plugin.getKaznaManager().addItem(Material.COBBLESTONE, cost);

        save();
        return true;
    }

    /**
     * Оценить рассрочку
     */
    public InstallmentResult evaluateInstallment(UUID uuid, String plotId, int cost,
                                                   org.bukkit.entity.Player player) {
        if (isPlotSold(plotId)) {
            return new InstallmentResult(false, 0, "Участок уже продан");
        }

        int minIncome = plugin.getConfig().getInt("property.installment.min-income", 500);
        double termMultiplier = plugin.getConfig().getDouble("property.installment.term-multiplier", 5);

        int income = plugin.getIncomeTracker().calculateIncome(uuid);
        int totalCobble = ChestScanner.countTotalMaterial(player, Material.COBBLESTONE);
        int totalIncome = income + totalCobble;

        if (totalIncome < minIncome) {
            return new InstallmentResult(false, 0, "Недостаточный доход: " + totalIncome
                    + " (мин: " + minIncome + ")");
        }

        int termDays = Math.max(1, (int) (((double) totalIncome / cost) * termMultiplier));
        termDays = Math.min(termDays, 30);

        return new InstallmentResult(true, termDays, "Одобрено на " + termDays + " игр. дней");
    }

    /**
     * Создать рассрочку
     */
    public void createInstallment(UUID uuid, String plotId, int cost, int termDays) {
        if (isPlotSold(plotId)) return;

        long now = System.currentTimeMillis();
        long dueDate = now + ((long) termDays * 20 * 60 * 1000);

        installments.put(uuid, new InstallmentData(plotId, cost, cost, termDays, now, dueDate));

        List<OwnedPlot> plots = ownedPlots.computeIfAbsent(uuid, k -> new ArrayList<>());
        plots.add(new OwnedPlot(plotId, now, false));

        soldPlots.put(plotId, uuid);

        save();
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

    // === Классы данных ===

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
}
