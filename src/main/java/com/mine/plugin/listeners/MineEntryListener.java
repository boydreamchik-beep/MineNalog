package com.mine.plugin.listeners;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.gui.MineLevelGUI;
import com.mine.plugin.managers.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MineEntryListener implements Listener {

    private final MinePlugin plugin;
    private final MineLevelGUI mineLevelGUI;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 3000;

    public MineEntryListener(MinePlugin plugin, MineLevelGUI mineLevelGUI) {
        this.plugin = plugin;
        this.mineLevelGUI = mineLevelGUI;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getFreezeManager().isFrozen(uuid)) return;

        ConfigManager cfg = plugin.getConfigManager();
        Location playerLoc = player.getLocation();

        double dx = playerLoc.getX() - cfg.getEntryX();
        double dy = playerLoc.getY() - cfg.getEntryY();
        double dz = playerLoc.getZ() - cfg.getEntryZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        double radius = cfg.getEntryRadius();

        if (distSq <= radius * radius) {
            long now = System.currentTimeMillis();
            Long last = cooldowns.get(uuid);
            if (last != null && (now - last) < COOLDOWN_MS) return;
            cooldowns.put(uuid, now);

            // Периодическая очистка кулдаунов вышедших игроков (защита от утечки памяти)
            if (cooldowns.size() > 256) {
                cooldowns.entrySet().removeIf(e -> (now - e.getValue()) > 60_000);
            }

            if (mineLevelGUI.isPlayerInMine(uuid)) {
                mineLevelGUI.openAlreadyInMineMenu(player);
            } else {
                mineLevelGUI.openMenu(player);
            }
        }
    }
}
