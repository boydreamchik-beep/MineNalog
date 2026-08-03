package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Замораживает игрока на месте пока он не выберет уровень шахты.
 * Игрок не может двигаться, но может крутить камеру.
 */
public class FreezeManager implements Listener {

    private final MinePlugin plugin;

    // UUID -> локация заморозки
    private final Map<UUID, Location> frozenPlayers = new HashMap<>();

    public FreezeManager(MinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Заморозить игрока на текущей позиции
     */
    public void freeze(Player player) {
        frozenPlayers.put(player.getUniqueId(), player.getLocation().clone());
    }

    /**
     * Разморозить игрока
     */
    public void unfreeze(Player player) {
        frozenPlayers.remove(player.getUniqueId());
    }

    public void unfreeze(UUID uuid) {
        frozenPlayers.remove(uuid);
    }

    /**
     * Проверить заморожен ли игрок
     */
    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.containsKey(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!frozenPlayers.containsKey(uuid)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Разрешаем вращение камеры, но не перемещение
        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {

            Location frozen = frozenPlayers.get(uuid);
            Location corrected = frozen.clone();
            corrected.setYaw(to.getYaw());
            corrected.setPitch(to.getPitch());
            event.setTo(corrected);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        frozenPlayers.remove(event.getPlayer().getUniqueId());
    }
}
