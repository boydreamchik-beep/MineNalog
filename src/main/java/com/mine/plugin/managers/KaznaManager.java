package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.utils.TaxUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * ИЗМЕНЕНИЯ:
 * - Добавлен метод getMaxPages()
 * - Добавлен метод getItemsForPage()
 * - Размер страницы = 45 слотов (54 - 9 под навигацию)
 * - Максимум 100 страниц
 */
public class KaznaManager {

    private final MinePlugin plugin;
    private final File kaznaFile;
    private FileConfiguration kaznaConfig;
    private final Map<Material, Integer> kaznaItems = new HashMap<>();

    // 54 слота (большой сундук) - 9 слотов навигации = 45 слотов для предметов
    public static final int ITEMS_PER_PAGE = 45;
    public static final int MAX_PAGES = 100;

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

    // ➕ НОВОЕ: Количество страниц
    public int getMaxPages() {
        int totalTypes = kaznaItems.size();
        if (totalTypes == 0) return 1;
        int pages = (int) Math.ceil((double) totalTypes / ITEMS_PER_PAGE);
        return Math.min(pages, MAX_PAGES);
    }

    // ➕ НОВОЕ: Получить предметы для конкретной страницы
    public Map<Material, Integer> getItemsForPage(int page) {
        Map<Material, Integer> pageItems = new HashMap<>();
        var allEntries = new java.util.ArrayList<>(kaznaItems.entrySet());

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allEntries.size());

        if (startIndex >= allEntries.size()) {
            return pageItems; // Пустая страница
        }

        for (int i = startIndex; i < endIndex; i++) {
            var entry = allEntries.get(i);
            pageItems.put(entry.getKey(), entry.getValue());
        }

        return pageItems;
    }
}
