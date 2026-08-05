package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Учёт дохода игрока.
 * Считает сколько блоков добыто и сколько налога уплачено.
 * Все изменения сохраняются асинхронно раз в минуту.
 */
public class IncomeTracker {

    private final MinePlugin plugin;
    private final File file;
    private FileConfiguration config;
    
    // Thread-safe коллекция для асинхронного доступа
    private final Map<UUID, IncomeData> incomes = new ConcurrentHashMap<>();
    
    // Флаг изменений для периодического сохранения
    private volatile boolean dirty = false;
    private BukkitTask autoSaveTask;

    public IncomeTracker(MinePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "incomes.yml");
    }

    public void load() {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
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
                        
                        // Не загружаем нулевые записи
                        if (minedTotal > 0 || taxPaidTotal > 0 || autoTaxPaid > 0) {
                            incomes.put(uuid, new IncomeData(minedTotal, taxPaidTotal, autoTaxPaid));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Ошибка загрузки incomes." + uuidStr + ": " + e.getMessage());
                    }
                }
            }
        }
        
        dirty = false;
    }

    /**
     * Запуск автоматического сохранения (вызывать из onEnable)
     */
    public void startAutoSave() {
        autoSaveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                saveAsync();
            }
        }, 20L * 60, 20L * 60); // Раз в минуту
    }

    /**
     * Остановка автосохранения (вызывать из onDisable)
     */
    public void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
    }

    /**
     * Синхронное сохранение - только для onDisable()
     */
    public void saveSync() {
        saveInternal();
    }

    /**
     * Асинхронное сохранение - вызывается при изменениях
     */
    public void saveAsync() {
        if (!dirty) return;

        // Копируем данные для безопасной записи в другом потоке
        final Map<UUID, IncomeData> snapshot = new HashMap<>();
        incomes.forEach((k, v) -> snapshot.put(k, new IncomeData(v.minedTotal, v.taxPaidTotal, v.autoTaxPaid)));
        
        dirty = false;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            writeToDisk(snapshot);
        });
    }

    private void saveInternal() {
        writeToDisk(incomes);
        dirty = false;
    }

    private void writeToDisk(Map<UUID, IncomeData> data) {
        FileConfiguration cfg = new YamlConfiguration();
        
        for (var entry : data.entrySet()) {
            String path = "incomes." + entry.getKey().toString();
            IncomeData d = entry.getValue();
            cfg.set(path + ".mined_total", d.minedTotal);
            cfg.set(path + ".tax_paid_total", d.taxPaidTotal);
            cfg.set(path + ".auto_tax_paid", d.autoTaxPaid);
        }
        
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить incomes.yml: " + e.getMessage());
        }
    }

    /**
     * Записать что игрок добыл блок в шахте
     */
    public void recordMined(UUID uuid, int amount) {
        if (amount <= 0) return;
        incomes.computeIfAbsent(uuid, k -> new IncomeData(0, 0, 0)).minedTotal += amount;
        dirty = true;
    }

    /**
     * Записать что у игрока забрали налог в шахте
     */
    public void recordTaxPaid(UUID uuid, int amount) {
        if (amount <= 0) return;
        incomes.computeIfAbsent(uuid, k -> new IncomeData(0, 0, 0)).taxPaidTotal += amount;
        dirty = true;
    }

    /**
     * Записать автоналог (земельный + автоматический)
     */
    public void recordAutoTax(UUID uuid, int amount) {
        if (amount <= 0) return;
        incomes.computeIfAbsent(uuid, k -> new IncomeData(0, 0, 0)).autoTaxPaid += amount;
        dirty = true;
    }

    /**
     * Получить данные о доходе игрока
     * @return IncomeData или новый объект с нулями если данных нет
     */
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

    /**
     * Очистить данные игрока (при необходимости)
     */
    public void clearIncome(UUID uuid) {
        incomes.remove(uuid);
        dirty = true;
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
