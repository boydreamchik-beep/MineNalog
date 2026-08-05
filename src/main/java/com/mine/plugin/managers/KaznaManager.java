package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управление казной города.
 * Хранит все ресурсы, собранные с налогов.
 * Все изменения сохраняются асинхронно раз в минуту.
 */
public class KaznaManager {

    public static final int ITEMS_PER_PAGE = 36; // Ровно 4 ряда по 9 слотов (слоты 9-44)

    private final MinePlugin plugin;
    private final File kaznaFile;
    private FileConfiguration kaznaConfig;
    
    // Thread-safe коллекция с сортировкой для стабильного GUI
    private final Map<Material, Integer> kaznaItems = new ConcurrentHashMap<>();
    
    // Флаг изменений для периодического сохранения
    private volatile boolean dirty = false;
    private BukkitTask autoSaveTask;

    public KaznaManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.kaznaFile = new File(plugin.getDataFolder(), "kazna.yml");
    }

    public void load() {
        if (!kaznaFile.getParentFile().exists()) {
            kaznaFile.getParentFile().mkdirs();
        }
        
        kaznaConfig = YamlConfiguration.loadConfiguration(kaznaFile);
        kaznaItems.clear();
        
        if (kaznaConfig.contains("items")) {
            var section = kaznaConfig.getConfigurationSection("items");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        Material mat = Material.valueOf(key.toUpperCase());
                        int amount = section.getInt(key);
                        if (amount > 0) {
                            kaznaItems.put(mat, amount);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Неизвестный материал в казне: " + key);
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
        final Map<Material, Integer> snapshot = new HashMap<>(kaznaItems);
        dirty = false;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            writeToDisk(snapshot);
        });
    }

    private void saveInternal() {
        writeToDisk(kaznaItems);
        dirty = false;
    }

    private void writeToDisk(Map<Material, Integer> data) {
        FileConfiguration cfg = new YamlConfiguration();
        
        for (var entry : data.entrySet()) {
            if (entry.getValue() > 0) {
                cfg.set("items." + entry.getKey().name(), entry.getValue());
            }
        }
        
        try {
            cfg.save(kaznaFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить kazna.yml: " + e.getMessage());
        }
    }

    /**
     * Добавить предмет в казну
     */
    public void addItem(Material material, int amount) {
        if (amount <= 0 || material == null || material.isAir()) return;
        kaznaItems.merge(material, amount, Integer::sum);
        dirty = true; // НЕ вызываем save() синхронно!
    }

    /**
     * Получить количество конкретного материала в казне
     */
    public int getAmount(Material m) {
        return kaznaItems.getOrDefault(m, 0);
    }

    /**
     * Получить все предметы (копия для безопасности)
     */
    public Map<Material, Integer> getAllItems() {
        return new HashMap<>(kaznaItems);
    }

    /**
     * Получить общее количество всех предметов в казне
     */
    public long getTotalItemCount() {
        return kaznaItems.values().stream().mapToLong(Integer::longValue).sum();
    }

    /**
     * Количество страниц (по стакам — каждый предмет раскладывается в ячейки по 64)
     */
    public int getMaxPages() {
        int perPage = ITEMS_PER_PAGE;
        int maxPages = plugin.getConfigManager().getKaznaMaxPages();

        int totalStacks = 0;
        for (var entry : kaznaItems.entrySet()) {
            int amount = entry.getValue();
            int maxStack = entry.getKey().getMaxStackSize();
            totalStacks += (int) Math.ceil((double) amount / maxStack);
        }

        if (totalStacks == 0) return 1;
        return Math.min((int) Math.ceil((double) totalStacks / perPage), maxPages);
    }

    /**
     * Получить предметы для страницы, разбитые на стаки.
     * Каждый элемент списка = одна ячейка в GUI.
     * Сортировка по алфавиту для стабильности страниц.
     */
    public List<Map.Entry<Material, Integer>> getItemsForPage(int page) {
        int perPage = ITEMS_PER_PAGE;

        List<Map.Entry<Material, Integer>> allStacks = new ArrayList<>();

        // Сортируем материалы по алфавиту для СТАБИЛЬНОСТИ страниц GUI
        List<Map.Entry<Material, Integer>> sortedEntries = kaznaItems.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparing(e -> e.getKey().name()))
                .toList();

        // Разбиваем с учетом реального maxStackSize предмета
        for (var entry : sortedEntries) {
            int amount = entry.getValue();
            int maxStack = entry.getKey().getMaxStackSize();

            while (amount > 0) {
                int stackSize = Math.min(amount, maxStack);
                allStacks.add(Map.entry(entry.getKey(), stackSize));
                amount -= stackSize;
            }
        }

        int start = page * perPage;
        if (start >= allStacks.size()) return Collections.emptyList();

        int end = Math.min(start + perPage, allStacks.size());
        return new ArrayList<>(allStacks.subList(start, end));
    }

    /**
     * Очистить казну (для админ-команд)
     */
    public void clear() {
        kaznaItems.clear();
        dirty = true;
    }
}
