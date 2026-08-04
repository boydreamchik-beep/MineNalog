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

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Конфиг (первым!)
        configManager = new ConfigManager(this);
        configManager.load();

        // Менеджеры данных
        kaznaManager = new KaznaManager(this);
        kaznaManager.load();

        creditManager = new CreditManager(this);
        creditManager.load();

        passportManager = new PassportManager(this);
        passportManager.load();

        taxTracker = new TaxTracker();
        freezeManager = new FreezeManager(this);
        mineGenerator = new MineGenerator(this);
        scoreboardManager = new ScoreboardManager(this);

        // GUI
        mineLevelGUI = new MineLevelGUI(this);

        // Регистрация слушателей
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(freezeManager, this);
        pm.registerEvents(mineLevelGUI, this);
        pm.registerEvents(new MineEntryListener(this, mineLevelGUI), this);
        pm.registerEvents(new MineBlockBreakListener(this, mineLevelGUI, taxTracker), this);
        pm.registerEvents(new CompassListener(this, mineLevelGUI), this);
        pm.registerEvents(mineGenerator, this);
        pm.registerEvents(scoreboardManager, this);

        // Запуск систем
        mineGenerator.start();
        scoreboardManager.start();
        creditManager.startReminders();

        // Команды
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

        // Команда перезагрузки конфига
        getCommand("minereload").setExecutor(new ReloadCommand(this));

        getLogger().info("=================================");
        getLogger().info("MinePlugin v4.0.0 загружен!");
        getLogger().info("Все настройки в config.yml");
        getLogger().info("/minereload для перезагрузки");
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
        if (scoreboardManager != null) scoreboardManager.stop();
        getLogger().info("MinePlugin выгружен. Данные сохранены.");
    }

    /**
     * Перезагрузка конфига без перезапуска сервера
     */
    public void reloadPlugin() {
        configManager.reload();
        if (scoreboardManager != null) {
            scoreboardManager.stop();
            scoreboardManager.start();
        }
        if (mineGenerator != null) {
            mineGenerator.start();
        }
        getLogger().info("Конфиг перезагружен!");
    }

    // Геттеры
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

    // === Внутренний класс команды /minereload ===
    private static class ReloadCommand implements CommandExecutor {
        private final MinePlugin plugin;
        ReloadCommand(MinePlugin plugin) { this.plugin = plugin; }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                  @NotNull String label, @NotNull String[] args) {
            if (!sender.hasPermission("mine.admin")) {
                sender.sendMessage(Component.text("Нет прав!")
                        .color(NamedTextColor.RED));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(Component.text("[MinePlugin] Конфиг перезагружен!")
                    .color(NamedTextColor.GREEN));
            return true;
        }
    }
}
