package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class KaznaManager {

    private final MinePlugin plugin;
    private final File kaznaFile;
    private FileConfiguration kaznaConfig;
    private final Map<Material, Integer> kaznaItems = new HashMap<>();

    public KaznaManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.kaznaFile = new File(plugin.getDataFolder(), "kazna.yml");
    }

    public void load() {
        if (!kaznaFile.exists()) {
            try { kaznaFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        kaznaConfig = YamlConfiguration.loadConfiguration(kaznaFile);
        kaznaItems.clear();
        if (kaznaConfig.contains("items")) {
            var section = kaznaConfig.getConfigurationSection("items");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        Material mat = Material.valueOf(key);
                        int amount = section.getInt(key);
                        if (amount > 0) kaznaItems.put(mat, amount);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    public void save() {
        kaznaConfig = new YamlConfiguration();
        for (var entry : kaznaItems.entrySet()) {
            kaznaConfig.set("items." + entry.getKey().name(), entry.getValue());
        }
        try { kaznaConfig.save(kaznaFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public void addItem(Material material, int amount) {
        if (amount <= 0) return;
        kaznaItems.merge(material, amount, Integer::sum);
        save();
    }

    public int getAmount(Material m) {
        return kaznaItems.getOrDefault(m, 0);
    }

    public Map<Material, Integer> getAllItems() {
        return new HashMap<>(kaznaItems);
    }

    public int getTotalItemCount() {
        return kaznaItems.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Количество страниц (по стакам — каждый предмет раскладывается в ячейки по 64)
     */
    public int getMaxPages() {
        int perPage = plugin.getConfigManager().getKaznaItemsPerPage();
        int maxPages = plugin.getConfigManager().getKaznaMaxPages();

        int totalStacks = 0;
        for (int amount : kaznaItems.values()) {
            totalStacks += (int) Math.ceil((double) amount / 64);
        }

        if (totalStacks == 0) return 1;
        return Math.min((int) Math.ceil((double) totalStacks / perPage), maxPages);
    }

    /**
     * Получить предметы для страницы, разбитые на стаки по 64.
     * Каждый элемент списка = одна ячейка в GUI.
     */
    public List<Map.Entry<Material, Integer>> getItemsForPage(int page) {
        int perPage = plugin.getConfigManager().getKaznaItemsPerPage();

        List<Map.Entry<Material, Integer>> allStacks = new ArrayList<>();

        // Разбиваем все предметы на стаки по 64
        for (var entry : kaznaItems.entrySet()) {
            int amount = entry.getValue();
            while (amount > 0) {
                int stackSize = Math.min(amount, 64);
                allStacks.add(Map.entry(entry.getKey(), stackSize));
                amount -= stackSize;
            }
        }

        // Вырезаем нужную страницу
        int start = page * perPage;
        int end = Math.min(start + perPage, allStacks.size());

        if (start >= allStacks.size()) return new ArrayList<>();
        return new ArrayList<>(allStacks.subList(start, end));
    }
}
