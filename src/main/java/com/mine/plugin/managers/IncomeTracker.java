package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Учёт дохода игрока.
 * Считает сколько блоков забрал налог в шахте у игрока (всего).
 */
public class IncomeTracker {

    private final MinePlugin plugin;
    private final File file;
    private FileConfiguration config;
    private final Map<UUID, IncomeData> incomes = new HashMap<>();

    public IncomeTracker(MinePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "incomes.yml");
    }

    public void load() {
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        config = YamlConfiguration.loadConfiguration(file);
        incomes.clear();

        if (config.contains("incomes")) {
            var section = config.getConfigurationSection("incomes");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        int minedTotal = section.getInt(uuidStr + ".mined_total", 0);
                        int taxPaidTotal = section.getInt(uuidStr + ".tax_paid_total", 0);
                        int autoTaxPaid = section.getInt(uuidStr + ".auto_tax_paid", 0);
                        incomes.put(uuid, new IncomeData(minedTotal, taxPaidTotal, autoTaxPaid));
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public void save() {
        config = new YamlConfiguration();
        for (var entry : incomes.entrySet()) {
            String path = "incomes." + entry.getKey().toString();
            IncomeData d = entry.getValue();
            config.set(path + ".mined_total", d.minedTotal);
            config.set(path + ".tax_paid_total", d.taxPaidTotal);
            config.set(path + ".auto_tax_paid", d.autoTaxPaid);
        }
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * Записать что игрок добыл блок в шахте
     */
    public void recordMined(UUID uuid, int amount) {
        IncomeData data = incomes.computeIfAbsent(uuid, k -> new IncomeData(0, 0, 0));
        data.minedTotal += amount;
        save();
    }

    /**
     * Записать что у игрока забрали налог в шахте
     */
    public void recordTaxPaid(UUID uuid, int amount) {
        IncomeData data = incomes.computeIfAbsent(uuid, k -> new IncomeData(0, 0, 0));
        data.taxPaidTotal += amount;
        save();
    }

    /**
     * Записать автоналог
     */
    public void recordAutoTax(UUID uuid, int amount) {
        IncomeData data = incomes.computeIfAbsent(uuid, k -> new IncomeData(0, 0, 0));
        data.autoTaxPaid += amount;
        save();
    }

    public IncomeData getIncome(UUID uuid) {
        return incomes.getOrDefault(uuid, new IncomeData(0, 0, 0));
    }

    /**
     * Рассчитать "доход" игрока для одобрения рассрочки.
     * Доход = добыто в шахте всего
     */
    public int calculateIncome(UUID uuid) {
        IncomeData data = getIncome(uuid);
        return data.minedTotal;
    }

    public static class IncomeData {
        public int minedTotal;
        public int taxPaidTotal;
        public int autoTaxPaid;

        public IncomeData(int minedTotal, int taxPaidTotal, int autoTaxPaid) {
            this.minedTotal = minedTotal;
            this.taxPaidTotal = taxPaidTotal;
            this.autoTaxPaid = autoTaxPaid;
        }
    }
}
