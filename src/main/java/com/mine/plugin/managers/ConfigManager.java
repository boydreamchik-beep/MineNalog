package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Единая точка чтения config.yml.
 *
 * Исправления v6.0.0:
 *  - Добавлены миры (шахта, генераторы, NPC, участок)
 *  - kazna.items-per-page ограничен 36 (реальное число слотов GUI)
 *  - Границы участка №1 читаются из конфига
 *  - Земельный налог, длительность игрового дня, радиус сканирования — из конфига
 *  - Валидация значений (нет деления на ноль и отрицательных интервалов)
 */
public class ConfigManager {

    private final MinePlugin plugin;
    private FileConfiguration config;

    // Миры
    private String mainWorldName;
    private String mineWorldName;
    private String propertyWorldName;

    // Точка входа
    private double entryX, entryY, entryZ, entryRadius;

    // Уровни
    private final List<LevelConfig> levels = new ArrayList<>();

    // Генераторы
    private final List<GeneratorConfig> generators = new ArrayList<>();
    private long regenDelayTicks;
    private final List<Material> generatorBlocks = new ArrayList<>();

    // Налоги
    private final Set<Material> allowedBlocks = new HashSet<>();
    private final Set<Material> treasuryOres = new HashSet<>();
    private final Set<Material> taxAsOriginal = new HashSet<>();

    // Магазин
    private final List<ShopItemConfig> shopItems = new ArrayList<>();
    private Material shopCurrency;
    private String shopCurrencyName;

    // Кредит
    private int creditMaxAmount;
    private double creditInterestRate;
    private int creditInterestDays;
    private boolean creditRequirePassport;

    // Казна
    private int kaznaItemsPerPage;
    private int kaznaMaxPages;

    // Табло
    private boolean scoreboardEnabled;
    private String scoreboardTitle;
    private int scoreboardUpdateInterval;
    private final List<String> scoreboardLines = new ArrayList<>();

    // Паспорт
    private String passportBirthPlace;
    private String passportCityName;

    // Имущество: NPC
    private double npcX, npcY, npcZ;

    // Имущество: участок №1
    private double plot1MinX, plot1MinY, plot1MinZ;
    private double plot1MaxX, plot1MaxY, plot1MaxZ;
    private int plot1PricePerBlock;
    private int plot1SurfaceBlocks;
    private String plot1Name;

    // Имущество: рассрочка
    private int installmentMinIncome;
    private double installmentOverdueRate;
    private double installmentTermMultiplier;
    private int installmentMaxTermDays;

    // Имущество: земельный налог
    private int landTaxAmount;
    private int landTaxIntervalGameDays;

    // Автоналог
    private boolean autoTaxEnabled;
    private double autoTaxRate;
    private int autoTaxIntervalGameDays;
    private Material autoTaxCurrency;

    // Сканирование контейнеров
    private int scanRadius;
    private boolean scanLoadedChunksOnly;

    // Время
    private int realMinutesPerGameDay;

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
        loadScan();
        loadTime();

        plugin.getLogger().info("Конфиг загружен. Уровней: " + levels.size()
                + ", генераторов: " + generators.size()
                + ", товаров: " + shopItems.size());
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

    public FileConfiguration getConfig() {
        return config;
    }

    // =====================================================
    // ЗАГРУЗКА СЕКЦИЙ
    // =====================================================

    private void loadWorlds() {
        mainWorldName = config.getString("worlds.main", "world");
        mineWorldName = config.getString("worlds.mine", mainWorldName);
        propertyWorldName = config.getString("worlds.property", mainWorldName);
    }

    private void loadEntryPoint() {
        entryX = config.getDouble("entry-point.x", -231.477);
        entryY = config.getDouble("entry-point.y", 59.0);
        entryZ = config.getDouble("entry-point.z", -46.454);
        entryRadius = Math.max(0.5, config.getDouble("entry-point.radius", 1.5));
    }

    private void loadLevels() {
        ConfigurationSection levelsSection = config.getConfigurationSection("levels");
        if (levelsSection == null) {
            plugin.getLogger().warning("Секция 'levels' не найдена в config.yml!");
            return;
        }

        for (String key : levelsSection.getKeys(false)) {
            ConfigurationSection sec = levelsSection.getConfigurationSection(key);
            if (sec == null) continue;

            LevelConfig level = new LevelConfig();
            level.id = key;
            level.enabled = sec.getBoolean("enabled", false);
            level.name = sec.getString("name", key);
            level.height = sec.getInt("height", 43);
            level.worldName = sec.getString("world", mineWorldName);

            level.teleportX = sec.getDouble("teleport.x", 0);
            level.teleportY = sec.getDouble("teleport.y", 64);
            level.teleportZ = sec.getDouble("teleport.z", 0);

            level.taxPercent = Math.max(0, Math.min(100, sec.getInt("tax-percent", 20)));

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

        regenDelayTicks = Math.max(1L, genSection.getLong("regen-delay-ticks", 1));

        for (String name : genSection.getStringList("blocks")) {
            try {
                generatorBlocks.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный блок генератора: " + name);
            }
        }
        if (generatorBlocks.isEmpty()) {
            generatorBlocks.add(Material.STONE);
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
            gen.worldName = sec.getString("world", mineWorldName);

            generators.add(gen);
        }
    }

    private void loadTax() {
        for (String name : config.getStringList("tax.allowed-blocks")) {
            try {
                allowedBlocks.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный блок в allowed-blocks: " + name);
            }
        }

        for (String name : config.getStringList("tax.treasury-ores")) {
            try {
                treasuryOres.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестная руда в treasury-ores: " + name);
            }
        }

        for (String name : config.getStringList("tax.tax-as-original")) {
            try {
                taxAsOriginal.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный блок в tax-as-original: " + name);
            }
        }
    }

    private void loadShop() {
        shopCurrency = parseMaterial(config.getString("shop.currency", "COBBLESTONE"),
                Material.COBBLESTONE);
        shopCurrencyName = config.getString("shop.currency-name", "Булыжник");

        ConfigurationSection itemsSection = config.getConfigurationSection("shop.items");
        if (itemsSection == null) return;

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection sec = itemsSection.getConfigurationSection(key);
            if (sec == null) continue;

            String matStr = sec.getString("material", "STONE");
            Material material;
            try {
                material = Material.valueOf(matStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неизвестный материал в магазине: " + matStr);
                continue;
            }

            ShopItemConfig item = new ShopItemConfig();
            item.id = key;
            item.name = sec.getString("name", key);
            item.material = material;
            item.price = Math.max(1, sec.getInt("price", 10));
            item.amount = Math.max(1, sec.getInt("amount", 1));
            item.description = sec.getString("description", "");

            shopItems.add(item);
        }
    }

    private void loadCredit() {
        creditMaxAmount = Math.max(1, config.getInt("credit.max-amount", 6400));
        creditInterestRate = Math.max(0.0, config.getDouble("credit.interest-rate", 0.03));
        creditInterestDays = Math.max(1, config.getInt("credit.interest-interval-days", 3));
        creditRequirePassport = config.getBoolean("credit.require-passport", true);
    }

    private void loadKazna() {
        int raw = config.getInt("kazna.items-per-page", 36);
        kaznaItemsPerPage = Math.max(1, Math.min(36, raw));
        if (raw > 36) {
            plugin.getLogger().warning("kazna.items-per-page уменьшен до 36 (реальное число слотов GUI).");
        }
        kaznaMaxPages = Math.max(1, config.getInt("kazna.max-pages", 100));
    }

    private void loadScoreboard() {
        scoreboardEnabled = config.getBoolean("scoreboard.enabled", true);
        scoreboardTitle = config.getString("scoreboard.title", "&6Topicus");
        scoreboardUpdateInterval = Math.max(1, config.getInt("scoreboard.update-interval", 40));
        scoreboardLines.addAll(config.getStringList("scoreboard.lines"));
    }

    private void loadPassport() {
        passportBirthPlace = config.getString("passport.default-birth-place", "Topicus, Энем");
        passportCityName = config.getString("passport.city-name", "Энем");
    }

    private void loadProperty() {
        npcX = config.getDouble("property.npc.x", -204.304);
        npcY = config.getDouble("property.npc.y", 66.0);
        npcZ = config.getDouble("property.npc.z", -24.418);

        ConfigurationSection plot = config.getConfigurationSection("property.plots.plot-1");
        if (plot != null) {
            plot1Name = plot.getString("name", "Участок №1");

            // config.yml хранит start-x/y/z + depth-down + height-up
            double startX = plot.getDouble("start-x", -239);
            double startY = plot.getDouble("start-y", 67);
            double startZ = plot.getDouble("start-z", -64);
            int depthDown = plot.getInt("depth-down", 3);
            int heightUp = plot.getInt("height-up", 20);

            // surface-blocks определяет количество блоков поверхности (площадь участка)
            // Пока 1 блок = стартовая точка; для >1 можно расширить область по X/Z
            plot1SurfaceBlocks = Math.max(1, plot.getInt("surface-blocks", 1));
            plot1PricePerBlock = Math.max(1, plot.getInt("price-per-block", 32));

            // Вычисляем bounding box участка от start-точки
            // 1 блок поверхности = 1×1 колонка, >1 — расширяем по X
            int sideLen = (int) Math.ceil(Math.sqrt(plot1SurfaceBlocks));
            plot1MinX = startX;
            plot1MinY = startY - depthDown;
            plot1MinZ = startZ;
            plot1MaxX = startX + sideLen - 1;
            plot1MaxY = startY + heightUp;
            plot1MaxZ = startZ + sideLen - 1;
        } else {
            plot1Name = "Участок №1";
            plot1MinX = -239; plot1MinY = 64; plot1MinZ = -64;
            plot1MaxX = -239; plot1MaxY = 87; plot1MaxZ = -64;
            plot1PricePerBlock = 32;
            plot1SurfaceBlocks = 1;
        }

        installmentMinIncome = Math.max(0, config.getInt("property.installment.min-income", 500));
        installmentOverdueRate = Math.max(0.0, config.getDouble("property.installment.overdue-rate", 0.03));
        installmentTermMultiplier = Math.max(0.1, config.getDouble("property.installment.term-multiplier", 5.0));
        installmentMaxTermDays = Math.max(1, config.getInt("property.installment.max-term-days", 30));

        landTaxAmount = Math.max(1, config.getInt("property.land-tax.amount", 128));
        landTaxIntervalGameDays = Math.max(1, config.getInt("property.land-tax.interval-game-days", 3));
    }

    private void loadAutoTax() {
        autoTaxEnabled = config.getBoolean("auto-tax.enabled", true);
        autoTaxRate = Math.max(0.0, Math.min(1.0, config.getDouble("auto-tax.rate", 0.20)));
        autoTaxIntervalGameDays = Math.max(1, config.getInt("auto-tax.interval-game-days", 3));
        autoTaxCurrency = parseMaterial(config.getString("auto-tax.currency", "COBBLESTONE"),
                Material.COBBLESTONE);
    }

    private void loadScan() {
        scanRadius = Math.max(8, Math.min(256, config.getInt("storage-scan.radius", 100)));
        scanLoadedChunksOnly = config.getBoolean("storage-scan.loaded-chunks-only", true);
    }

    private void loadTime() {
        realMinutesPerGameDay = Math.max(1, config.getInt("game-time.real-minutes-per-game-day", 20));
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неизвестный материал: " + name + " → " + fallback.name());
            return fallback;
        }
    }

    // =====================================================
    // ГЕТТЕРЫ
    // =====================================================

    public String getMainWorldName() { return mainWorldName; }
    public String getMineWorldName() { return mineWorldName; }
    public String getPropertyWorldName() { return propertyWorldName; }
    public String getPlot1World() { return propertyWorldName; }
    public String getNpcWorldName() { return propertyWorldName; }

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

    public String getPassportBirthPlace() { return passportBirthPlace; }
    public String getPassportCityName() { return passportCityName; }

    public double getNpcX() { return npcX; }
    public double getNpcY() { return npcY; }
    public double getNpcZ() { return npcZ; }

    public String getPlot1Name() { return plot1Name; }
    public double getPlot1MinX() { return plot1MinX; }
    public double getPlot1MinY() { return plot1MinY; }
    public double getPlot1MinZ() { return plot1MinZ; }
    public double getPlot1MaxX() { return plot1MaxX; }
    public double getPlot1MaxY() { return plot1MaxY; }
    public double getPlot1MaxZ() { return plot1MaxZ; }
    public int getPlot1PricePerBlock() { return plot1PricePerBlock; }
    public int getPlot1SurfaceBlocks() { return plot1SurfaceBlocks; }
    public int getPlot1TotalPrice() { return plot1PricePerBlock * plot1SurfaceBlocks; }

    public int getInstallmentMinIncome() { return installmentMinIncome; }
    public double getInstallmentOverdueRate() { return installmentOverdueRate; }
    public double getInstallmentTermMultiplier() { return installmentTermMultiplier; }
    public int getInstallmentMaxTermDays() { return installmentMaxTermDays; }

    public int getLandTaxAmount() { return landTaxAmount; }
    public int getLandTaxIntervalGameDays() { return landTaxIntervalGameDays; }

    public boolean isAutoTaxEnabled() { return autoTaxEnabled; }
    public double getAutoTaxRate() { return autoTaxRate; }
    public int getAutoTaxIntervalGameDays() { return autoTaxIntervalGameDays; }
    public Material getAutoTaxCurrency() { return autoTaxCurrency; }

    public int getScanRadius() { return scanRadius; }
    public boolean isScanLoadedChunksOnly() { return scanLoadedChunksOnly; }

    public int getRealMinutesPerGameDay() { return realMinutesPerGameDay; }

    /** Длительность одного игрового дня в миллисекундах реального времени. */
    public long getGameDayMillis() {
        return (long) realMinutesPerGameDay * 60L * 1000L;
    }

    /** Длительность одного игрового дня в тиках. */
    public long getGameDayTicks() {
        return (long) realMinutesPerGameDay * 60L * 20L;
    }

    // =====================================================
    // КЛАССЫ КОНФИГА
    // =====================================================

    public static class LevelConfig {
        public String id;
        public boolean enabled;
        public String name;
        public int height;
        public String worldName;
        public double teleportX, teleportY, teleportZ;
        public int taxPercent;
        public double zoneMinX, zoneMaxX, zoneMinY, zoneMaxY, zoneMinZ, zoneMaxZ;

        /** Каждый N-й блок уходит в налог. Integer.MAX_VALUE = налога нет. */
        public int getTaxEvery() {
            if (taxPercent <= 0) return Integer.MAX_VALUE;
            int every = (int) Math.round(100.0 / taxPercent);
            return Math.max(1, every);
        }

        public boolean isInZone(Location loc) {
            if (loc == null || loc.getWorld() == null) return false;
            if (!loc.getWorld().getName().equals(worldName)) return false;

            double x = loc.getX(), y = loc.getY(), z = loc.getZ();
            return x >= Math.min(zoneMinX, zoneMaxX) && x <= Math.max(zoneMinX, zoneMaxX)
                    && y >= Math.min(zoneMinY, zoneMaxY) && y <= Math.max(zoneMinY, zoneMaxY)
                    && z >= Math.min(zoneMinZ, zoneMaxZ) && z <= Math.max(zoneMinZ, zoneMaxZ);
        }
    }

    public static class GeneratorConfig {
        public String id;
        public boolean enabled;
        public int x, y, z;
        public String worldName;
    }

    public static class ShopItemConfig {
        public String id;
        public String name;
        public Material material;
        public int price;
        public int amount;
        public String description;
    }
}
