package com.mine.plugin;

import com.mine.plugin.commands.KaznaCommand;
import com.mine.plugin.gui.MineLevelGUI;
import com.mine.plugin.listeners.CompassListener;
import com.mine.plugin.listeners.MineBlockBreakListener;
import com.mine.plugin.listeners.MineEntryListener;
import com.mine.plugin.managers.FreezeManager;
import com.mine.plugin.managers.KaznaManager;
import com.mine.plugin.managers.MineGenerator;    // ➕ НОВОЕ
import com.mine.plugin.managers.TaxTracker;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MinePlugin extends JavaPlugin {

    private static MinePlugin instance;
    private KaznaManager kaznaManager;
    private MineLevelGUI mineLevelGUI;
    private TaxTracker taxTracker;
    private FreezeManager freezeManager;
    private MineGenerator mineGenerator;          // ➕ НОВОЕ

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Инициализация менеджеров
        kaznaManager = new KaznaManager(this);
        kaznaManager.load();

        taxTracker = new TaxTracker();
        freezeManager = new FreezeManager(this);
        mineGenerator = new MineGenerator(this);  // ➕ НОВОЕ

        // Инициализация GUI
        mineLevelGUI = new MineLevelGUI(this);

        // Регистрация слушателей
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(freezeManager, this);
        pm.registerEvents(mineLevelGUI, this);
        pm.registerEvents(new MineEntryListener(this, mineLevelGUI), this);
        pm.registerEvents(new MineBlockBreakListener(this, mineLevelGUI, taxTracker), this);
        pm.registerEvents(new CompassListener(this, mineLevelGUI), this);
        pm.registerEvents(mineGenerator, this);   // ➕ НОВОЕ

        // Запуск генератора
        mineGenerator.start();                    // ➕ НОВОЕ

        // Регистрация команд
        KaznaCommand kaznaCommand = new KaznaCommand(this);
        getCommand("kazna").setExecutor(kaznaCommand);
        getCommand("kazna").setTabCompleter(kaznaCommand);
        pm.registerEvents(kaznaCommand, this);

        getLogger().info("=================================");
        getLogger().info("MinePlugin v1.0.0 загружен!");
        getLogger().info("Шахта активна. Казна готова.");
        getLogger().info("Генератор блоков запущен.");       // ➕ НОВОЕ
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        if (kaznaManager != null) {
            kaznaManager.save();
        }
        getLogger().info("MinePlugin выгружен. Казна сохранена.");
    }

    public static MinePlugin getInstance() {
        return instance;
    }

    public KaznaManager getKaznaManager() {
        return kaznaManager;
    }

    public MineLevelGUI getMineLevelGUI() {
        return mineLevelGUI;
    }

    public TaxTracker getTaxTracker() {
        return taxTracker;
    }

    public FreezeManager getFreezeManager() {
        return freezeManager;
    }

    // ➕ НОВОЕ
    public MineGenerator getMineGenerator() {
        return mineGenerator;
    }
}
