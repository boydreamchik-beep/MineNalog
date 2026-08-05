package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class IncomeTracker {

    private final MinePlugin plugin;
    private final File file;
    private final Map<UUID, IncomeData> incomes = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;
    private BukkitTask autoSaveTask;

    public IncomeTracker(MinePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "incomes.yml");
    }

    public void load() {
        file.getParentFile().mkdirs();
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        var cfg = YamlConfiguration.loadConfiguration(file);
        incomes.clear();
        if (cfg.contains("incomes")) {
            var section = cfg.getConfigurationSection("incomes");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        int minedTotal = section.getInt(uuidStr + ".mined_total", 0);
                        int taxPaidTotal = section.getInt(uuidStr + ".tax_paid_total", 0);
                        int autoTaxPaid = section.getInt(uuidStr + ".auto_tax_paid", 0);
                        if (minedTotal > 0 || taxPaidTotal > 0 || autoTaxPaid > 0) {
                            incomes.put(uuid, new IncomeData(minedTotal, taxPaidTotal, autoTaxPaid));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Битый UUID в incomes.yml: " + uuidStr);
                    }
                }
            }
        }
    }

    public void recordMined(UUID uuid, int amount) {
        if (amount <= 0) return;
        computeIfAbsent(uuid).minedTotal += amount;
        dirty = true;
    }

    public void recordTaxPaid(UUID uuid, int amount) {
        if (amount <= 0) return;
        computeIfAbsent(uuid).taxPaidTotal += amount;
        dirty = true;
    }

    public void recordAutoTax(UUID uuid, int amount) {
        if (amount <= 0) return;
        computeIfAbsent(uuid).autoTaxPaid += amount;
        dirty = true;
    }

    public IncomeData getIncome(UUID uuid) {
        return incomes.getOrDefault(uuid, new IncomeData());
    }

    public int calculateIncome(UUID uuid) {
        IncomeData data = getIncome(uuid);
        return data.minedTotal;
    }

    public void startAutoSave() {
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) saveSync();
        }, 1200L, 1200L); // раз в минуту
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) autoSaveTask.cancel();
    }

    /** Для onDisable. */
    public void saveSync() {
        Map<UUID, IncomeData> snapshot = new HashMap<>();
        incomes.forEach((k, v) -> snapshot.put(k, new IncomeData(v)));
        dirty = false;

        var cfg = new YamlConfiguration();
        for (var e : snapshot.entrySet()) {
            String path = "incomes." + e.getKey();
            cfg.set(path + ".mined_total", e.getValue().minedTotal);
            cfg.set(path + ".tax_paid_total", e.getValue().taxPaidTotal);
            cfg.set(path + ".auto_tax_paid", e.getValue().autoTaxPaid);
        }
        try { cfg.save(file); } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить incomes.yml", ex);
        }
    }

    private IncomeData computeIfAbsent(UUID uuid) {
        return incomes.computeIfAbsent(uuid, k -> new IncomeData());
    }

    public static class IncomeData {
        public int minedTotal, taxPaidTotal, autoTaxPaid;
        public IncomeData() { this(0, 0, 0); }
        public IncomeData(int mt, int tpt, int atp) { minedTotal=mt; taxPaidTotal=tpt; autoTaxPaid=atp; }
    }
}
