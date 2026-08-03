package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
            try {
                kaznaFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать kazna.yml!");
                e.printStackTrace();
            }
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
                        if (amount > 0) {
                            kaznaItems.put(mat, amount);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Неизвестный материал в казне: " + key);
                    }
                }
            }
        }

        plugin.getLogger().info("Казна загружена. Типов ресурсов: " + kaznaItems.size());
    }

    public void save() {
        kaznaConfig = new YamlConfiguration();

        for (Map.Entry<Material, Integer> entry : kaznaItems.entrySet()) {
            kaznaConfig.set("items." + entry.getKey().name(), entry.getValue());
        }

        try {
            kaznaConfig.save(kaznaFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить казну!");
            e.printStackTrace();
        }
    }

    public void addItem(Material material, int amount) {
        if (amount <= 0) return;
        kaznaItems.merge(material, amount, Integer::sum);
        save();
    }

    public int getAmount(Material material) {
        return kaznaItems.getOrDefault(material, 0);
    }

    public Map<Material, Integer> getAllItems() {
        return new HashMap<>(kaznaItems);
    }

    public boolean removeItem(Material material, int amount) {
        int current = kaznaItems.getOrDefault(material, 0);
        if (current < amount) return false;

        int newAmount = current - amount;
        if (newAmount <= 0) {
            kaznaItems.remove(material);
        } else {
            kaznaItems.put(material, newAmount);
        }
        save();
        return true;
    }

    public int getTotalItemCount() {
        return kaznaItems.values().stream().mapToInt(Integer::intValue).sum();
    }
}
