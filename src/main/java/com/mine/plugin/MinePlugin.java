package com.mine.plugin;

import com.mine.plugin.commands.*;
import com.mine.plugin.gui.MineLevelGUI;
import com.mine.plugin.listeners.*;
import com.mine.plugin.managers.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class MinePlugin extends JavaPlugin {

    private static MinePlugin instance;
    private ConfigManager configManager;
    private KaznaManager kaznaManager;
    private MineLevelGUI mineLevelGUI;
    private TaxTracker taxTracker;
    private FreezeManager freezeManager;
    private MineGenerator mineGenerator;
    private ScoreboardManager scoreboardManager;
    private CreditManager creditManager;
    private PassportManager passportManager;
    private IncomeTracker incomeTracker;
    private PropertyManager propertyManager;
    private TaxCollector taxCollector;
    private NPCListener npcListener;

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        configManager = new ConfigManager(this);
        configManager.load();

        kaznaManager = new KaznaManager(this);
        kaznaManager.load();

        creditManager = new CreditManager(this);
        creditManager.load();

        passportManager = new PassportManager(this);
        passportManager.load();

        incomeTracker = new IncomeTracker(this);
        incomeTracker.load();

        propertyManager = new PropertyManager(this);
        propertyManager.load();

        taxTracker = new TaxTracker();
        freezeManager = new FreezeManager(this);
        mineGenerator = new MineGenerator(this);
        scoreboardManager = new ScoreboardManager(this);
        taxCollector = new TaxCollector(this);

        mineLevelGUI = new MineLevelGUI(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(freezeManager, this);
        pm.registerEvents(mineLevelGUI, this);
        pm.registerEvents(new MineEntryListener(this, mineLevelGUI), this);
        pm.registerEvents(new MineBlockBreakListener(this, mineLevelGUI, taxTracker), this);
        pm.registerEvents(new CompassListener(this, mineLevelGUI), this);
        pm.registerEvents(mineGenerator, this);
        pm.registerEvents(scoreboardManager, this);
        pm.registerEvents(propertyManager, this);  // Защита участка

        mineGenerator.start();
        scoreboardManager.start();
        creditManager.startReminders();
        taxCollector.start();
        propertyManager.startLandTaxReminder();
        propertyManager.startInstallmentChecker();

        KaznaCommand kaznaCommand = new KaznaCommand(this);
        getCommand("kazna").setExecutor(kaznaCommand);
        getCommand("kazna").setTabCompleter(kaznaCommand);
        pm.registerEvents(kaznaCommand, this);

        ShopCommand shopCommand = new ShopCommand(this);
        getCommand("buy").setExecutor(shopCommand);
        getCommand("buy").setTabCompleter(shopCommand);
        pm.registerEvents(shopCommand, this);

        CreditCommand creditCommand = new CreditCommand(this);
        getCommand("credit").setExecutor(creditCommand);
        getCommand("credit").setTabCompleter(creditCommand);

        PassportCommand passportCommand = new PassportCommand(this);
        getCommand("passport").setExecutor(passportCommand);
        getCommand("passport").setTabCompleter(passportCommand);

        PropertyCommand propertyCommand = new PropertyCommand(this);
        getCommand("property").setExecutor(propertyCommand);
        getCommand("property").setTabCompleter(propertyCommand);
        pm.registerEvents(propertyCommand, this);

        npcListener = new NPCListener(this, propertyCommand);
        pm.registerEvents(npcListener, this);
        npcListener.spawnNPC();

        getCommand("minereload").setExecutor(new ReloadCommand(this));

        getLogger().info("=================================");
        getLogger().info("MinePlugin v5.2.0 загружен!");
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        if (kaznaManager != null) kaznaManager.save();
        if (creditManager != null) {
            creditManager.save();
            creditManager.stopReminders();
        }
        if (passportManager != null) passportManager.save();
        if (incomeTracker != null) incomeTracker.save();
        if (propertyManager != null) {
            propertyManager.save();
            propertyManager.stopLandTaxReminder();
        }
        if (scoreboardManager != null) scoreboardManager.stop();
        if (taxCollector != null) taxCollector.stop();
        if (npcListener != null) npcListener.despawnNPC();
        getLogger().info("MinePlugin выгружен.");
    }

    public void reloadPlugin() {
        configManager.reload();
        if (scoreboardManager != null) {
            scoreboardManager.stop();
            scoreboardManager.start();
        }
        if (mineGenerator != null) mineGenerator.start();
        if (npcListener != null) {
            npcListener.despawnNPC();
            npcListener.spawnNPC();
        }
        getLogger().info("Конфиг перезагружен!");
    }

    public static MinePlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public KaznaManager getKaznaManager() { return kaznaManager; }
    public MineLevelGUI getMineLevelGUI() { return mineLevelGUI; }
    public TaxTracker getTaxTracker() { return taxTracker; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public MineGenerator getMineGenerator() { return mineGenerator; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public CreditManager getCreditManager() { return creditManager; }
    public PassportManager getPassportManager() { return passportManager; }
    public IncomeTracker getIncomeTracker() { return incomeTracker; }
    public PropertyManager getPropertyManager() { return propertyManager; }
    public TaxCollector getTaxCollector() { return taxCollector; }

    private static class ReloadCommand implements CommandExecutor {
        private final MinePlugin plugin;
        ReloadCommand(MinePlugin plugin) { this.plugin = plugin; }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                  @NotNull String label, @NotNull String[] args) {
            if (!sender.hasPermission("mine.admin")) {
                sender.sendMessage(Component.text("Нет прав!").color(NamedTextColor.RED));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(Component.text("[MinePlugin] Конфиг перезагружен!")
                    .color(NamedTextColor.GREEN));
            return true;
        }
    }
}
