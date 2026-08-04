package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Управление имуществом — участки земли.
 */
public class PropertyManager {

    private final MinePlugin plugin;
    private final File file;
    private FileConfiguration config;

    // UUID -> список купленных участков
    private final Map<UUID, List<OwnedPlot>> ownedPlots = new HashMap<>();

    // UUID -> рассрочки
    private final Map<UUID, InstallmentData> installments = new HashMap<>();

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

        // Загрузка участков
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
                        var instSection = section.getConfigurationSection(uuidStr);
                        if (instSection != null) {
                            String plotId = instSection.getString("plot_id", "");
                            int totalCost = instSection.getInt("total_cost", 0);
                            int remaining = instSection.getInt("remaining", 0);
                            int termDays = instSection.getInt("term_days", 0);
                            long startDate = instSection.getLong("start_date", 0);
                            long dueDate = instSection.getLong("due_date", 0);
                            installments.put(uuid, new InstallmentData(
                                    plotId, totalCost, remaining, termDays, startDate, dueDate));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public void save() {
        config = new YamlConfiguration();

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

        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Купить участок за полную стоимость
     */
    public boolean buyPlotFull(UUID uuid, String plotId, int cost, org.bukkit.entity.Player player) {
        int total = ChestScanner.countTotalMaterial(player, Material.COBBLESTONE);
        if (total < cost) return false;

        ChestScanner.removeMaterialFromAll(player, Material.COBBLESTONE, cost);

        List<OwnedPlot> plots = ownedPlots.computeIfAbsent(uuid, k -> new ArrayList<>());
        plots.add(new OwnedPlot(plotId, System.currentTimeMillis(), true));

        // Добавляем стоимость в казну
        plugin.getKaznaManager().addItem(Material.COBBLESTONE, cost);

        save();
        return true;
    }

    /**
     * Оценить и одобрить рассрочку
     */
    public InstallmentResult evaluateInstallment(UUID uuid, String plotId, int cost,
                                                   org.bukkit.entity.Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        int minIncome = cfg.getConfig().getInt("property.installment.min-income", 500);
        double termMultiplier = cfg.getConfig().getDouble("property.installment.term-multiplier", 5);

        int income = plugin.getIncomeTracker().calculateIncome(uuid);
        int totalCobble = ChestScanner.countTotalMaterial(player, Material.COBBLESTONE);

        // Общий "доход" = добытое + имеющееся
        int totalIncome = income + totalCobble;

        if (totalIncome < minIncome) {
            return new InstallmentResult(false, 0, "Недостаточный доход: " + totalIncome
                    + " (мин: " + minIncome + ")");
        }

        // Срок = доход / стоимость * множитель
        int termDays = Math.max(1, (int) (((double) totalIncome / cost) * termMultiplier));
        termDays = Math.min(termDays, 30); // Макс 30 игровых дней

        return new InstallmentResult(true, termDays, "Одобрено на " + termDays + " игровых дней");
    }

    /**
     * Создать рассрочку
     */
    public void createInstallment(UUID uuid, String plotId, int cost, int termDays) {
        long now = System.currentTimeMillis();
        // 1 игровой день = 20 минут реального времени
        long dueDate = now + ((long) termDays * 20 * 60 * 1000);

        installments.put(uuid, new InstallmentData(plotId, cost, cost, termDays, now, dueDate));

        List<OwnedPlot> plots = ownedPlots.computeIfAbsent(uuid, k -> new ArrayList<>());
        plots.add(new OwnedPlot(plotId, now, false));

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
