package com.mine.plugin;

import com.mine.plugin.commands.CreditCommand;
import com.mine.plugin.commands.KaznaCommand;
import com.mine.plugin.commands.PassportCommand;
import com.mine.plugin.commands.ShopCommand;
import com.mine.plugin.gui.MineLevelGUI;
import com.mine.plugin.listeners.CompassListener;
import com.mine.plugin.listeners.MineBlockBreakListener;
import com.mine.plugin.listeners.MineEntryListener;
import com.mine.plugin.managers.CreditManager;
import com.mine.plugin.managers.FreezeManager;
import com.mine.plugin.managers.KaznaManager;
import com.mine.plugin.managers.MineGenerator;
import com.mine.plugin.managers.PassportManager;
import com.mine.plugin.managers.ScoreboardManager;
import com.mine.plugin.managers.TaxTracker;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ИЗМЕНЕНИЯ:
 * - Добавлен CreditManager
 * - Добавлен PassportManager
 * - Добавлены команды /credit и /passport
 */
public class MinePlugin extends JavaPlugin {

    private static MinePlugin instance;
    private KaznaManager kaznaManager;
    private MineLevelGUI mineLevelGUI;
    private TaxTracker taxTracker;
    private FreezeManager freezeManager;
    private MineGenerator mineGenerator;
    private ScoreboardManager scoreboardManager;
    private CreditManager creditManager;       // ➕ НОВОЕ
    private PassportManager passportManager;   // ➕ НОВОЕ

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Менеджеры
        kaznaManager = new KaznaManager(this);
        kaznaManager.load();

        creditManager = new CreditManager(this);     // ➕ НОВОЕ
        creditManager.load();                         // ➕ НОВОЕ

        passportManager = new PassportManager(this);  // ➕ НОВОЕ
        passportManager.load();                       // ➕ НОВОЕ

        taxTracker = new TaxTracker();
        freezeManager = new FreezeManager(this);
        mineGenerator = new MineGenerator(this);
        scoreboardManager = new ScoreboardManager(this);

        // GUI
        mineLevelGUI = new MineLevelGUI(this);

        // Регистрация
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
        creditManager.startReminders();                // ➕ НОВОЕ

        // Команды
        KaznaCommand kaznaCommand = new KaznaCommand(this);
        getCommand("kazna").setExecutor(kaznaCommand);
        getCommand("kazna").setTabCompleter(kaznaCommand);
        pm.registerEvents(kaznaCommand, this);

        ShopCommand shopCommand = new ShopCommand(this);
        getCommand("buy").setExecutor(shopCommand);
        getCommand("buy").setTabCompleter(shopCommand);
        pm.registerEvents(shopCommand, this);

        CreditCommand creditCommand = new CreditCommand(this);  // ➕ НОВОЕ
        getCommand("credit").setExecutor(creditCommand);         // ➕ НОВОЕ
        getCommand("credit").setTabCompleter(creditCommand);     // ➕ НОВОЕ

        PassportCommand passportCommand = new PassportCommand(this);  // ➕ НОВОЕ
        getCommand("passport").setExecutor(passportCommand);          // ➕ НОВОЕ
        getCommand("passport").setTabCompleter(passportCommand);      // ➕ НОВОЕ

        getLogger().info("=================================");
        getLogger().info("MinePlugin v3.0.0 загружен!");
        getLogger().info("Шахта, казна, магазин, табло,");
        getLogger().info("кредиты, паспорта.");
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

    public static MinePlugin getInstance() { return instance; }
    public KaznaManager getKaznaManager() { return kaznaManager; }
    public MineLevelGUI getMineLevelGUI() { return mineLevelGUI; }
    public TaxTracker getTaxTracker() { return taxTracker; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public MineGenerator getMineGenerator() { return mineGenerator; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public CreditManager getCreditManager() { return creditManager; }         // ➕ НОВОЕ
    public PassportManager getPassportManager() { return passportManager; }   // ➕ НОВОЕ
}
