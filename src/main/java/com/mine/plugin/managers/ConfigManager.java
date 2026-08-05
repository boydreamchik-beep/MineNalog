package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class ConfigManager {

    private final MinePlugin plugin;
    private FileConfiguration config;

    // === ТОЧКА ВХОДА ===
    private double entryX, entryY, entryZ, entryRadius;
    private String mineWorldName = "world";

    // === УРОВНИ ===
    private final List<LevelConfig> levels = new ArrayList<>();

    // === ГЕНЕРАТОРЫ ===
    private final List<GeneratorConfig> generators = new ArrayList<>();
    private long regenDelayTicks;
    private final List<Material> generatorBlocks = new ArrayList<>();

    // === НАЛОГИ ===
    private final Set<Material> allowedBlocks = new HashSet<>();
    private final Set<Material> treasuryOres = new HashSet<>();
    private final Set<Material> taxAsOriginal = new HashSet<>();

    // === МАГАЗИН ===
    private final List<ShopItemConfig> shopItems = new ArrayList<>();
    private Material shopCurrency;
    private String shopCurrencyName;

    // === КРЕДИТ ===
    private int creditMaxAmount;
    private double creditInterestRate;
    private int creditInterestDays;
    private boolean creditRequirePassport;

    // === КАЗНА ===
    private int kaznaItemsPerPage = 36; // ← ИСПРАВЛЕНО с 45 на 36
    private int kaznaMaxPages;

    // === ТАБЛО ===
    private boolean scoreboardEnabled;
    private String scoreboardTitle;
    private int scoreboardUpdateInterval;
    private final List<String> scoreboardLines = new ArrayList<>();

    // === ПАСПОРТ ===
    private String passportDefaultBirthPlace;

    // === ИМУЩЕСТВО ===
    private double propertyNpcX, propertyNpcY, propertyNpcZ;
    private String propertyNpcWorld = "world";

    private double plot1MinX, plot1MinY, plot1MinZ;
    private double plot1MaxX, plot1MaxY, plot1MaxZ;
    private int plot1PricePerBlock;
    private int plot1SurfaceBlocks;

    private int installmentMinIncome;
    private double installmentOverdueRate;
    private double installmentTermMultiplier;

    // === АВТОНАЛОГ ===
    private boolean autoTaxEnabled;
    private double autoTaxRate;
    private int autoTaxIntervalGameDays;
    private Material autoTaxCurrency;

    // === CHEST SCANNER ===
    private int scanRadiusBlocks = 100; // чанков, ~7-8

    // === МИР ===
    private String mainWorldName = "world";

    public ConfigManager(MinePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        loadWorlds();
        loadEntryPoint();
        loadLevels();
        loadGenerators();
        loadTax();
        loadShop();
        loadCredit();
        loadKazna();
        loadScoreboard();
        loadPassport();
        loadProperty();
        loadAutoTax();

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
        load();
    }

    public FileConfiguration getConfig() { return config; }

    private void loadWorlds() {
        mainWorldName = config.getString("world", "world");
        mineWorldName = mainWorldName; // по умолчанию тот же
        mineWorldName = config.getString("entry-point.world", mainWorldName);
    }

    private void loadEntryPoint() {
        entryX = config.getDouble("entry-point.x", -231.477);
        entryY = config.getDouble("entry-point.y", 59.0);
        entryZ = config.getDouble("entry-point.z", -46.454);
        entryRadius = Math.max(0.5, config.getDouble("entry-point.radius", 1.5));
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

            // Зона
            ConfigurationSection zone = sec.getConfigurationSection("zone");
            if (zone != null) {
                level.zoneMinX = zone.getDouble("min-x", -280);
                level.zoneMaxX = zone.getDouble("max-x", -180);
                level.zoneMinY = zone.getDouble("min-y", 30);
                level.zoneMaxY = zone.getDouble("max-y", 50);
                level.zoneMinZ = zone.getDouble("min-z", -120);
                level.zoneMaxZ = zone.getDouble("max-z", -20);
            } else {
                level.enabled = false;
            }

            // Мир (можно указать для каждого уровня отдельно)
            level.worldName = sec.getString("world", mineWorldName);

            levels.add(level);
        }
    }

    private void loadGenerators() {
        ConfigurationSection genSection = config.getConfigurationSection("generators");
        if (genSection == null) return;

        regenDelayTicks = Math.max(1, genSection.getLong("regen-delay-ticks", 1));

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
            gen.worldName = sec.getString("world", mainWorldName);

            generators.add(gen);
        }
    }

    private void loadTax() {
        List<String> allowed = config.getStringList("tax.allowed-blocks");
        for (String name : allowed) {
            try { allowedBlocks.add(Material.valueOf(name.toUpperCase())); }
            catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный блок в allowed-blocks: " + name);
            }
        }

        List<String> ores = config.getStringList("tax.treasury-ores");
        for (String name : ores) {
            try { treasuryOres.add(Material.valueOf(name.toUpperCase())); }
            catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестная руда: " + name);
            }
        }

        List<String> taxOrig = config.getStringList("tax.tax-as-original");
        for (String name : taxOrig) {
            try { taxAsOriginal.add(Material.valueOf(name.toUpperCase())); }
            catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный блок в tax-as-original: " + name);
            }
        }
    }

    private void loadShop() {
        String currencyStr = config.getString("shop.currency", "COBBLESTONE");
        try { shopCurrency = Material.valueOf(currencyStr.toUpperCase()); }
        catch (IllegalArgumentException e) { shopCurrency = Material.COBBLESTONE; }
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
            try { item.material = Material.valueOf(matStr.toUpperCase()); }
            catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный материал в магазине: " + matStr); continue;
            }
            item.price = Math.max(0, sec.getInt("price", 10));
            item.description = sec.getString("description", "");
            shopItems.add(item);
        }
    }

    private void loadCredit() {
        creditMaxAmount = Math.max(1, config.getInt("credit.max-amount", 6400));
        creditInterestRate = Math.max(0, config.getDouble("credit.interest-rate", 0.03));
        creditInterestDays = Math.max(1, config.getInt("credit.interest-interval-days", 3));
        creditRequirePassport = config.getBoolean("credit.require-passport", true);
    }

    private void loadKazna() {
        kaznaItemsPerPage = Math.min(Math.max(1, config.getInt("kazna.items-per-page", 36)), 54 - 18);
        kaznaMaxPages = Math.max(1, config.getInt("kazna.max-pages", 100));
    }

    private void loadScoreboard() {
        scoreboardEnabled = config.getBoolean("scoreboard.enabled", true);
        scoreboardTitle = config.getString("scoreboard.title", "Topicus");
        scoreboardUpdateInterval = Math.max(1, config.getInt("scoreboard.update-interval", 40));
        scoreboardLines.addAll(config.getStringList("scoreboard.lines"));
    }

    private void loadPassport() {
        passportDefaultBirthPlace = config.getString("passport.default-birth-place", "Topicus, Энем");
    }

    private void loadProperty() {
        ConfigurationSection npcSec = config.getConfigurationSection("property.npc");
        if (npcSec != null) {
            propertyNpcX = npcSec.getDouble("x", -204.304);
            propertyNpcY = npcSec.getDouble("y", 66.0);
            propertyNpcZ = npcSec.getDouble("z", -24.418);
            propertyNpcWorld = npcSec.getString("world", mainWorldName);
        }

        // Участок №1
        ConfigurationSection plotSec = config.getConfigurationSection("property.plots.plot-1");
        if (plotSec != null) {
            plot1MinX = plotSec.getDouble("min-x", -239.300);
            plot1MinY = plotSec.getDouble("min-y", 64.0);
            plot1MinZ = plotSec.getDouble("min-z", -63.525);
            plot1MaxX = plotSec.getDouble("max-x", -229.458);
            plot1MaxY = plotSec.getDouble("max-y", 87.0);
            plot1MaxZ = plotSec.getDouble("max-z", -43.642);
            plot1PricePerBlock = Math.max(1, plotSec.getInt("price-per-block", 32));
            plot1SurfaceBlocks = Math.max(1, plotSec.getInt("surface-blocks", 1));
        } else {
            // Fallback старого формата
            plot1MinX = -239.300; plot1MinY = 64.0; plot1MinZ = -63.525;
            plot1MaxX = -229.458; plot1MaxY = 87.0; plot1MaxZ = -43.642;
            plot1PricePerBlock = 32; plot1SurfaceBlocks = 1;
        }

        // Рассрочка
        ConfigurationSection instSec = config.getConfigurationSection("property.installment");
        if (instSec != null) {
            installmentMinIncome = instSec.getInt("min-income", 500);
            installmentOverdueRate = Math.max(0, instSec.getDouble("overdue-rate", 0.03));
            installmentTermMultiplier = Math.max(0.1, instSec.getDouble("term-multiplier", 5));
        } else {
            installmentMinIncome = 500; installmentOverdueRate = 0.03; installmentTermMultiplier = 5;
        }
    }

    private void loadAutoTax() {
        autoTaxEnabled = config.getBoolean("auto-tax.enabled", true);
        autoTaxRate = Math.max(0, Math.min(1, config.getDouble("auto-tax.rate", 0.20)));
        autoTaxIntervalGameDays = Math.max(1, config.getInt("auto-tax.interval-game-days", 3));
        String cur = config.getString("auto-tax.currency", "COBBLESTONE");
        try { autoTaxCurrency = Material.valueOf(cur.toUpperCase()); }
        catch (Exception e) { autoTaxCurrency = Material.COBBLESTONE; }

        scanRadiusBlocks = config.getInt("storage-scan.radius", 100);
    }

    // ============================================
    // ГЕТТЕРЫ
    // ============================================

    public String getMainWorldName() { return mainWorldName; }
    public String getMineWorldName() { return mineWorldName; }

    public double getEntryX() { return entryX; }
    public double getEntryY() { return entryY; }
    public double getEntryZ() { return entryZ; }
    public double getEntryRadius() { return entryRadius; }

    public List<LevelConfig> getLevels() { return levels; }

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

    public String getPassportBirthPlace() { return passportDefaultBirthPlace; }

    public double getPropertyNpcX() { return propertyNpcX; }
    public double getPropertyNpcY() { return propertyNpcY; }
    public double getPropertyNpcZ() { return propertyNpcZ; }
    public String getPropertyNpcWorld() { return propertyNpcWorld; }

    public double getPlot1MinX() { return plot1MinX; }
    public double getPlot1MinY() { return plot1MinY; }
    public double getPlot1MinZ() { return plot1MinZ; }
    public double getPlot1MaxX() { return plot1MaxX; }
    public double getPlot1MaxY() { return plot1MaxY; }
    public double getPlot1MaxZ() { return plot1MaxZ; }
    public int getPlot1PricePerBlock() { return plot1PricePerBlock; }
    public int getPlot1SurfaceBlocks() { return plot1SurfaceBlocks; }

    public int getInstallmentMinIncome() { return installmentMinIncome; }
    public double getInstallmentOverdueRate() { return installmentOverdueRate; }
    public double getInstallmentTermMultiplier() { return installmentTermMultiplier; }

    public boolean isAutoTaxEnabled() { return autoTaxEnabled; }
    public double getAutoTaxRate() { return autoTaxRate; }
    public int getAutoTaxIntervalGameDays() { return autoTaxIntervalGameDays; }
    public Material getAutoTaxCurrency() { return autoTaxCurrency; }

    public int getScanRadiusBlocks() { return scanRadiusBlocks; }

    // ============================================
    // КЛАССЫ
    // ============================================

    public static class LevelConfig {
        public String id;
        public boolean enabled;
        public String name;
        public int height;
        public double teleportX, teleportY, teleportZ;
        public int taxPercent;
        public double zoneMinX, zoneMaxX, zoneMinY, zoneMaxY, zoneMinZ, zoneMaxZ;
        public String worldName; // ← НОВОЕ

        public int getTaxEvery() {
            if (taxPercent <= 0) return Integer.MAX_VALUE;
            return (int) Math.round(100.0 / taxPercent);
        }

        /** Проверяет, находится ли точка в зоне. */
        public boolean isInZone(org.bukkit.Location loc) {
            if (!loc.getWorld().getName().equals(worldName)) return false;
            return loc.getX() >= zoneMinX && loc.getX() <= zoneMaxX &&
                   loc.getY() >= zoneMinY && loc.getY() <= zoneMaxY &&
                   loc.getZ() >= zoneMinZ && loc.getZ() <= zoneMaxZ;
        }
    }

    public static class GeneratorConfig {
        public String id;
        public boolean enabled;
        public int x, y, z;
        public String worldName; // ← НОВОЕ
    }

    public static class ShopItemConfig {
        public String id, name, description;
        public Material material;
        public int price;
    }
}
