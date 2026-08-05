package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управляет казной города. Async-safe.
 */
public class KaznaManager {

    private final MinePlugin plugin;
    private final File kaznaFile;
    private final Map<Material, Integer> kaznaItems = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;
    private BukkitTask autoSaveTask;

    public static final int ITEMS_PER_PAGE = 36; // 9..44

    public KaznaManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.kaznaFile = new File(plugin.getDataFolder(), "kazna.yml");
    }

    public void load() {
        kaznaFile.getParentFile().mkdirs();
        if (!kaznaFile.exists()) {
            try { kaznaFile.createNewFile(); } catch (IOException ignored) {}
        }
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(kaznaFile);
        kaznaItems.clear();
        if (cfg.contains("items")) {
            var section = cfg.getConfigurationSection("items");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        Material mat = Material.valueOf(key);
                        int amount = section.getInt(key, 0);
                        if (amount > 0) kaznaItems.put(mat, amount);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    public void addItem(Material material, int amount) {
        if (amount <= 0 || material == null || material.isAir()) return;
        kaznaItems.merge(material, amount, Integer::sum);
        dirty = true;
    }

    public int getAmount(Material m) {
        return kaznaItems.getOrDefault(m, 0);
    }

    public Map<Material, Integer> getAllItems() {
        return new HashMap<>(kaznaItems);
    }

    public long getTotalItemCount() {
        return kaznaItems.values().stream().mapToLong(Integer::longValue).sum();
    }

    public int getMaxPages() {
        ConfigManager cfg = plugin.getConfigManager();
        int perPage = ITEMS_PER_PAGE; // игнорируем conf, чтобы всегда совпадало с GUI
        int maxPages = cfg.getKaznaMaxPages();

        long totalStacks = 0;
        for (var entry : kaznaItems.entrySet()) {
            totalStacks += (long) Math.ceil((double) entry.getValue() / entry.getKey().getMaxStackSize());
        }
        if (totalStacks == 0) return 1;
        return Math.min((int) Math.ceil((double) totalStacks / perPage), maxPages);
    }

    public List<Map.Entry<Material, Integer>> getItemsForPage(int page) {
        int perPage = ITEMS_PER_PAGE;
        List<Map.Entry<Material, Integer>> allStacks = new ArrayList<>();

        // Сортируем для стабильности страниц (алфавит по названию материала)
        kaznaItems.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparing(e -> e.getKey().name()))
                .forEach(entry -> {
                    int amount = entry.getValue();
                    int maxStack = entry.getKey().getMaxStackSize();
                    while (amount > 0) {
                        int stackSize = Math.min(amount, maxStack);
                        allStacks.add(Map.entry(entry.getKey(), stackSize));
                        amount -= stackSize;
                    }
                });

        int start = page * perPage;
        if (start >= allStacks.size()) return Collections.emptyList();
        int end = Math.min(start + perPage, allStacks.size());
        return new ArrayList<>(allStacks.subList(start, end));
    }

    /** Запустить фоновое сохранение каждые 2 минуты. */
    public void startAutoSave() {
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) saveSync();
        }, 2400L, 2400L);
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) autoSaveTask.cancel();
    }

    /** Синхронное сохранение — только для onDisable! */
    public void saveSync() {
        Map<Material, Integer> snapshot = new HashMap<>(kaznaItems);
        dirty = false;

        var cfg = new org.bukkit.configuration.file.YamlConfiguration();
        for (var entry : snapshot.entrySet()) {
            cfg.set("items." + entry.getKey().name(), entry.getValue());
        }
        try { cfg.save(kaznaFile); } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить kazna.yml: " + e.getMessage());
        }
    }
}
