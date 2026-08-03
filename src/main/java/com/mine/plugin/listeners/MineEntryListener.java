package com.mine.plugin.listeners;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.gui.MineLevelGUI;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ИЗМЕНЕНИЯ:
 * 1. Если игрок уже в шахте и наступает на точку — открывается
 *    окно "Вы уже в шахте" с кнопкой выхода вместо меню выбора
 * 2. Добавлена проверка на замороженных игроков (не открывать повторно)
 */
public class MineEntryListener implements Listener {

    private final MinePlugin plugin;
    private final MineLevelGUI mineLevelGUI;

    private static final double ENTRY_X = -231.477;
    private static final double ENTRY_Y = 59.0;
    private static final double ENTRY_Z = -46.454;
    private static final double RADIUS = 1.5;

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

        // Не открываем, если игрок заморожен (уже смотрит меню)
        if (plugin.getFreezeManager().isFrozen(uuid)) {
            return;
        }

        Location playerLoc = player.getLocation();
        double dx = playerLoc.getX() - ENTRY_X;
        double dy = playerLoc.getY() - ENTRY_Y;
        double dz = playerLoc.getZ() - ENTRY_Z;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq <= RADIUS * RADIUS) {
            // Проверяем кулдаун
            long now = System.currentTimeMillis();
            Long lastOpen = cooldowns.get(uuid);
            if (lastOpen != null && (now - lastOpen) < COOLDOWN_MS) {
                return;
            }
            cooldowns.put(uuid, now);

            // ===== ИЗМЕНЕНИЕ: Проверяем в шахте ли игрок =====
            if (mineLevelGUI.isPlayerInMine(uuid)) {
                // Игрок уже в шахте — показываем окно "нельзя выбрать"
                mineLevelGUI.openAlreadyInMineMenu(player);
            } else {
                // Обычное меню выбора уровня
                mineLevelGUI.openMenu(player);
            }
        }
    }
}
