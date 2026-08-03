package com.mine.plugin;

import com.mine.plugin.commands.KaznaCommand;
import com.mine.plugin.commands.ShopCommand;
import com.mine.plugin.gui.MineLevelGUI;
import com.mine.plugin.listeners.CompassListener;
import com.mine.plugin.listeners.MineBlockBreakListener;
import com.mine.plugin.listeners.MineEntryListener;
import com.mine.plugin.managers.FreezeManager;
import com.mine.plugin.managers.KaznaManager;
import com.mine.plugin.managers.MineGenerator;
import com.mine.plugin.managers.ScoreboardManager;
import com.mine.plugin.managers.TaxTracker;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ИЗМЕНЕНИЯ:
 * - Добавлен ScoreboardManager (табло)
 * - Добавлен ShopCommand (/buy)
 * - Добавлены геттеры
 */
public class MinePlugin extends JavaPlugin {

    private static MinePlugin instance;
    private KaznaManager kaznaManager;
    private MineLevelGUI mineLevelGUI;
    private TaxTracker taxTracker;
    private FreezeManager freezeManager;
    private MineGenerator mineGenerator;
    private ScoreboardManager scoreboardManager;  // ➕ НОВОЕ

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Менеджеры
        kaznaManager = new KaznaManager(this);
        kaznaManager.load();

        taxTracker = new TaxTracker();
        freezeManager = new FreezeManager(this);
        mineGenerator = new MineGenerator(this);
        scoreboardManager = new ScoreboardManager(this);  // ➕ НОВОЕ

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
        pm.registerEvents(scoreboardManager, this);       // ➕ НОВОЕ

        // Запуск систем
        mineGenerator.start();
        scoreboardManager.start();                        // ➕ НОВОЕ

        // Команды
        KaznaCommand kaznaCommand = new KaznaCommand(this);
        getCommand("kazna").setExecutor(kaznaCommand);
        getCommand("kazna").setTabCompleter(kaznaCommand);
        pm.registerEvents(kaznaCommand, this);

        ShopCommand shopCommand = new ShopCommand(this);  // ➕ НОВОЕ
        getCommand("buy").setExecutor(shopCommand);       // ➕ НОВОЕ
        getCommand("buy").setTabCompleter(shopCommand);   // ➕ НОВОЕ
        pm.registerEvents(shopCommand, this);             // ➕ НОВОЕ

        getLogger().info("=================================");
        getLogger().info("MinePlugin v2.0.0 загружен!");
        getLogger().info("Шахта, казна, магазин, табло.");
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        if (kaznaManager != null) kaznaManager.save();
        if (scoreboardManager != null) scoreboardManager.stop();  // ➕ НОВОЕ
        getLogger().info("MinePlugin выгружен.");
    }

    public static MinePlugin getInstance() { return instance; }
    public KaznaManager getKaznaManager() { return kaznaManager; }
    public MineLevelGUI getMineLevelGUI() { return mineLevelGUI; }
    public TaxTracker getTaxTracker() { return taxTracker; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public MineGenerator getMineGenerator() { return mineGenerator; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }  // ➕ НОВОЕ
}
