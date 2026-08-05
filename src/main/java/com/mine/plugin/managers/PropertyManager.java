package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PropertyManager implements Listener {

    private final MinePlugin plugin;
    private final File file;
    private YamlConfiguration config;
    private final Map<UUID, List<OwnedPlot>> ownedPlots = new HashMap<>();
    private final Map<UUID, InstallmentData> installments = new HashMap<>();
    private final Map<String, UUID> soldPlots = new HashMap<>();
    private final Map<UUID, Long> landTaxLastPaid = new HashMap<>(); // timestamp
    private BukkitTask landTaxTask, installmentTask;
    private boolean dirty = false;
    private BukkitTask autoSaveTask;

    // Затавка берётся из ConfigManager больше!

    public PropertyManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "properties.yml");
    }

    public void load() {
        file.getParentFile().mkdirs();
        if (!file.exists()) try { file.createNewFile(); } catch (IOException ignored) {};
        config = YamlConfiguration.loadConfiguration(file);
        ownedPlots.clear(); installments.clear(); soldPlots.clear(); landTaxLastPaid.clear();

        // sold-plots
        if (config.contains("sold-plots")) {
            var s = config.getConfigurationSection("sold-plots");
            if (s!=null) for (String pid:s.getKeys(false))
                try { soldPlots.put(pid, UUID.fromString(s.getString(pid))); }
                catch(Exception ignored){}
        }
        // owned
        if (config.contains("owned")) { /* ... аналогично ... */ }
        // installments
        if (config.contains("installments")) { /* ... аналогично ... */ }
        // land-tax-last-paid
        if (config.contains("land-tax-last-paid")) {
            var s = config.getConfigurationSection("land-tax-last-paid");
            if (s!=null) for (String uid:s.getKeys(false))
                try { landTaxLastPaid.put(UUID.fromString(uid), s.getLong(uid)); }
                catch(Exception ignored){}
        }
    }

    public void markDirty() { dirty=true; }
    public void saveSync() { /* полная перезапись */ dirty=false; }
    public void startAutoSave() { autoSaveTask=Bukkit.getScheduler().runTaskTimer(plugin, ()->{
        if(dirty)saveSync();
    }, 2400L, 2400L);}
    public void stopAutoSave(){if(autoSaveTask!=null)autoSaveTask.cancel();}
    // ... rest of logic same but with:
    // - ConfigManager getters for coordinates instead of constants
    // - landTaxLastPaid checks before reminders
    // - navertivanie otsrochki tsiklom while(not>dueDate)
    // - no sync save in methods except explicit call
}
