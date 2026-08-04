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

    public int getAmount(Material m) { return kaznaItems.getOrDefault(m, 0); }
    public Map<Material, Integer> getAllItems() { return new HashMap<>(kaznaItems); }
    public int getTotalItemCount() { return kaznaItems.values().stream().mapToInt(Integer::intValue).sum(); }

    public int getMaxPages() {
        int perPage = plugin.getConfigManager().getKaznaItemsPerPage();
        int maxPages = plugin.getConfigManager().getKaznaMaxPages();
        int totalTypes = kaznaItems.size();
        if (totalTypes == 0) return 1;
        return Math.min((int) Math.ceil((double) totalTypes / perPage), maxPages);
    }

    public Map<Material, Integer> getItemsForPage(int page) {
        int perPage = plugin.getConfigManager().getKaznaItemsPerPage();
        Map<Material, Integer> pageItems = new LinkedHashMap<>();
        var all = new ArrayList<>(kaznaItems.entrySet());
        int start = page * perPage;
        int end = Math.min(start + perPage, all.size());
        if (start >= all.size()) return pageItems;
        for (int i = start; i < end; i++) {
            var e = all.get(i);
            pageItems.put(e.getKey(), e.getValue());
        }
        return pageItems;
    }
}
