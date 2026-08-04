package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Центральный менеджер конфигов.
 * Все настройки читаются отсюда.
 */
public class ConfigManager {

    private final MinePlugin plugin;
    private FileConfiguration config;

    // Кэшированные значения
    private double entryX, entryY, entryZ, entryRadius;
    private final List<LevelConfig> levels = new ArrayList<>();
    private final List<GeneratorConfig> generators = new ArrayList<>();
    private long regenDelayTicks;
    private final List<Material> generatorBlocks = new ArrayList<>();
    private final Set<Material> allowedBlocks = new HashSet<>();
    private final Set<Material> treasuryOres = new HashSet<>();
    private final Set<Material> taxAsOriginal = new HashSet<>();
    private final List<ShopItemConfig> shopItems = new ArrayList<>();
    private Material shopCurrency;
    private String shopCurrencyName;
    private int creditMaxAmount;
    private double creditInterestRate;
    private int creditInterestDays;
    private boolean creditRequirePassport;
    private int kaznaItemsPerPage;
    private int kaznaMaxPages;
    private boolean scoreboardEnabled;
    private String scoreboardTitle;
    private int scoreboardUpdateInterval;
    private final List<String> scoreboardLines = new ArrayList<>();
    private final Map<String, String> messages = new HashMap<>();

    public ConfigManager(MinePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        loadEntryPoint();
        loadLevels();
        loadGenerators();
        loadTax();
        loadShop();
        loadCredit();
        loadKazna();
        loadScoreboard();
        loadMessages();

        plugin.getLogger().info("Конфиг загружен успешно.");
    }

    public void reload() {
        levels.clear();
        generators.clear();
        generatorBlocks.clear();
        allowedBlocks.clear();
        treasuryOres.clear();
        taxAsOriginal.clear();
        shopItems.clear();
        scoreboardLines.clear();
        messages.clear();
        load();
    }

    // === ЗАГРУЗКА СЕКЦИЙ ===

    private void loadEntryPoint() {
        entryX = config.getDouble("entry-point.x", -231.477);
        entryY = config.getDouble("entry-point.y", 59.0);
        entryZ = config.getDouble("entry-point.z", -46.454);
        entryRadius = config.getDouble("entry-point.radius", 1.5);
    }

    private void loadLevels() {
        ConfigurationSection levelsSection = config.getConfigurationSection("levels");
        if (levelsSection == null) return;

        for (String key : levelsSection.getKeys(false)) {
            ConfigurationSection sec = levelsSection.getConfigurationSection(key);
            if (sec == null) continue;

            LevelConfig level = new LevelConfig();
            level.id = key;
            level.enabled = sec.getBoolean("enabled", false);
            level.name = sec.getString("name", key);
            level.height = sec.getInt("height", 43);
            level.teleportX = sec.getDouble("teleport.x", 0);
            level.teleportY = sec.getDouble("teleport.y", 0);
            level.teleportZ = sec.getDouble("teleport.z", 0);
            level.taxPercent = sec.getInt("tax-percent", 20);
            level.zoneMinX = sec.getDouble("zone.min-x", -280);
            level.zoneMaxX = sec.getDouble("zone.max-x", -180);
            level.zoneMinY = sec.getDouble("zone.min-y", 30);
            level.zoneMaxY = sec.getDouble("zone.max-y", 50);
            level.zoneMinZ = sec.getDouble("zone.min-z", -120);
            level.zoneMaxZ = sec.getDouble("zone.max-z", -20);

            levels.add(level);
        }
    }

    private void loadGenerators() {
        ConfigurationSection genSection = config.getConfigurationSection("generators");
        if (genSection == null) return;

        regenDelayTicks = genSection.getLong("regen-delay-ticks", 1);

        List<String> blockNames = genSection.getStringList("blocks");
        for (String name : blockNames) {
            try {
                generatorBlocks.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный блок генератора: " + name);
            }
        }

        for (String key : genSection.getKeys(false)) {
            if (key.equals("regen-delay-ticks") || key.equals("blocks")) continue;

            ConfigurationSection sec = genSection.getConfigurationSection(key);
            if (sec == null) continue;

            GeneratorConfig gen = new GeneratorConfig();
            gen.id = key;
            gen.enabled = sec.getBoolean("enabled", true);
            gen.x = sec.getInt("x", 0);
            gen.y = sec.getInt("y", 42);
            gen.z = sec.getInt("z", 0);

            generators.add(gen);
        }
    }

    private void loadTax() {
        List<String> allowed = config.getStringList("tax.allowed-blocks");
        for (String name : allowed) {
            try {
                allowedBlocks.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный блок в allowed-blocks: " + name);
            }
        }

        List<String> ores = config.getStringList("tax.treasury-ores");
        for (String name : ores) {
            try {
                treasuryOres.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестная руда: " + name);
            }
        }

        List<String> taxOrig = config.getStringList("tax.tax-as-original");
        for (String name : taxOrig) {
            try {
                taxAsOriginal.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный блок в tax-as-original: " + name);
            }
        }
    }

    private void loadShop() {
        String currencyStr = config.getString("shop.currency", "COBBLESTONE");
        try {
            shopCurrency = Material.valueOf(currencyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            shopCurrency = Material.COBBLESTONE;
        }
        shopCurrencyName = config.getString("shop.currency-name", "Булыжник");

        ConfigurationSection itemsSection = config.getConfigurationSection("shop.items");
        if (itemsSection == null) return;

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection sec = itemsSection.getConfigurationSection(key);
            if (sec == null) continue;

            ShopItemConfig item = new ShopItemConfig();
            item.id = key;
            item.name = sec.getString("name", key);
            String matStr = sec.getString("material", "STONE");
            try {
                item.material = Material.valueOf(matStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный материал в магазине: " + matStr);
                continue;
            }
            item.price = sec.getInt("price", 10);
            item.description = sec.getString("description", "");

            shopItems.add(item);
        }
    }

    private void loadCredit() {
        creditMaxAmount = config.getInt("credit.max-amount", 6400);
        creditInterestRate = config.getDouble("credit.interest-rate", 0.03);
        creditInterestDays = config.getInt("credit.interest-interval-days", 3);
        creditRequirePassport = config.getBoolean("credit.require-passport", true);
    }

    private void loadKazna() {
        kaznaItemsPerPage = config.getInt("kazna.items-per-page", 45);
        kaznaMaxPages = config.getInt("kazna.max-pages", 100);
    }

    private void loadScoreboard() {
        scoreboardEnabled = config.getBoolean("scoreboard.enabled", true);
        scoreboardTitle = config.getString("scoreboard.title", "✦ Topicus ✦");
        scoreboardUpdateInterval = config.getInt("scoreboard.update-interval", 40);
        scoreboardLines.addAll(config.getStringList("scoreboard.lines"));
    }

    private void loadMessages() {
        ConfigurationSection msgSection = config.getConfigurationSection("messages");
        if (msgSection == null) return;

        for (String key : msgSection.getKeys(false)) {
            messages.put(key, msgSection.getString(key, ""));
        }
    }

    // === ГЕТТЕРЫ ===

    public double getEntryX() { return entryX; }
    public double getEntryY() { return entryY; }
    public double getEntryZ() { return entryZ; }
    public double getEntryRadius() { return entryRadius; }

    public List<LevelConfig> getLevels() { return levels; }
    public LevelConfig getLevel(int index) {
        if (index >= 0 && index < levels.size()) return levels.get(index);
        return null;
    }
    public LevelConfig getEnabledLevel(int index) {
        int count = 0;
        for (LevelConfig level : levels) {
            if (level.enabled) {
                if (count == index) return level;
                count++;
            }
        }
        return null;
    }

    public List<GeneratorConfig> getGenerators() { return generators; }
    public long getRegenDelayTicks() { return regenDelayTicks; }
    public List<Material> getGeneratorBlocks() { return generatorBlocks; }

    public Set<Material> getAllowedBlocks() { return allowedBlocks; }
    public Set<Material> getTreasuryOres() { return treasuryOres; }
    public Set<Material> getTaxAsOriginal() { return taxAsOriginal; }

    public boolean isTreasuryOre(Material m) { return treasuryOres.contains(m); }
    public boolean isAllowedBlock(Material m) { return allowedBlocks.contains(m); }
    public boolean isTaxAsOriginal(Material m) { return taxAsOriginal.contains(m); }

    public List<ShopItemConfig> getShopItems() { return shopItems; }
    public Material getShopCurrency() { return shopCurrency; }
    public String getShopCurrencyName() { return shopCurrencyName; }

    public int getCreditMaxAmount() { return creditMaxAmount; }
    public double getCreditInterestRate() { return creditInterestRate; }
    public int getCreditInterestDays() { return creditInterestDays; }
    public boolean isCreditRequirePassport() { return creditRequirePassport; }

    public int getKaznaItemsPerPage() { return kaznaItemsPerPage; }
    public int getKaznaMaxPages() { return kaznaMaxPages; }

    public boolean isScoreboardEnabled() { return scoreboardEnabled; }
    public String getScoreboardTitle() { return scoreboardTitle; }
    public int getScoreboardUpdateInterval() { return scoreboardUpdateInterval; }
    public List<String> getScoreboardLines() { return scoreboardLines; }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "");
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String msg = getMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return msg;
    }

    // === КЛАССЫ КОНФИГОВ ===

    public static class LevelConfig {
        public String id;
        public boolean enabled;
        public String name;
        public int height;
        public double teleportX, teleportY, teleportZ;
        public int taxPercent;
        public double zoneMinX, zoneMaxX, zoneMinY, zoneMaxY, zoneMinZ, zoneMaxZ;

        public int getTaxEvery() {
            if (taxPercent <= 0) return Integer.MAX_VALUE;
            return (int) Math.round(100.0 / taxPercent);
        }
    }

    public static class GeneratorConfig {
        public String id;
        public boolean enabled;
        public int x, y, z;
    }

    public static class ShopItemConfig {
        public String id;
        public String name;
        public Material material;
        public int price;
        public String description;
    }
}
