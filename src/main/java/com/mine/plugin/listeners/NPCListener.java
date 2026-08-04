package com.mine.plugin.listeners;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.commands.PropertyCommand;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NPC имущества.
 * Когда игрок подходит к координатам NPC и нажимает ПКМ — открывается меню.
 * (Так как Paper не имеет встроенных NPC, используем зону рядом с координатами)
 */
public class NPCListener implements Listener {

    private final MinePlugin plugin;
    private final PropertyCommand propertyCommand;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public NPCListener(MinePlugin plugin, PropertyCommand propertyCommand) {
        this.plugin = plugin;
        this.propertyCommand = propertyCommand;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        double npcX = plugin.getConfig().getDouble("property.npc.x", -204.304);
        double npcY = plugin.getConfig().getDouble("property.npc.y", 66.0);
        double npcZ = plugin.getConfig().getDouble("property.npc.z", -24.418);
        double radius = plugin.getConfig().getDouble("property.npc.radius", 2.0);

        Location loc = player.getLocation();
        double dx = loc.getX() - npcX;
        double dy = loc.getY() - npcY;
        double dz = loc.getZ() - npcZ;

        if (dx * dx + dy * dy + dz * dz <= radius * radius) {
            long now = System.currentTimeMillis();
            Long last = cooldowns.get(uuid);
            if (last != null && now - last < 3000) return;
            cooldowns.put(uuid, now);

            propertyCommand.openPropertyMenu(player);
        }
    }
}
