package com.mine.plugin.listeners;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.gui.MineLevelGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * ИЗМЕНЕНИЯ:
 * - giveCompass() теперь НЕ заменяет предмет в 9 слоте
 * - Сначала сохраняет предмет из слота 8, потом ставит компас
 * - Если слот 8 занят — предмет перемещается в первый свободный слот
 * - Если инвентарь полон — предмет дропается на землю
 */
public class CompassListener implements Listener {

    private final MinePlugin plugin;
    private final MineLevelGUI mineLevelGUI;

    private static final double EXIT_X = -231.477;
    private static final double EXIT_Y = 59.0;
    private static final double EXIT_Z = -46.454;

    public static final String COMPASS_NAME = "Выход из шахты";

    public CompassListener(MinePlugin plugin, MineLevelGUI mineLevelGUI) {
        this.plugin = plugin;
        this.mineLevelGUI = mineLevelGUI;
    }

    /**
     * Создаёт компас шахтёра
     */
    public static ItemStack createMineCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();

        meta.displayName(Component.text(COMPASS_NAME)
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.empty(),
                Component.text("ПКМ — выйти из шахты")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Вас телепортирует на поверхность")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        compass.setItemMeta(meta);
        return compass;
    }

    /**
     * Проверяет является ли предмет нашим компасом
     */
    public static boolean isMineCompass(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.COMPASS) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (!meta.hasDisplayName()) return false;

        String displayName = net.kyori.adventure.text.serializer.plain
                .PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName());

        return displayName.contains(COMPASS_NAME);
    }

    /**
     * Выдать компас игроку БЕЗ ПОТЕРИ предмета в 9 слоте.
     * 
     * ИЗМЕНЕНИЕ:
     * - Если слот 8 (9-й слот хотбара) занят — сохраняем предмет
     * - Предмет перемещается в первый свободный слот
     * - Если нет свободного — дропается на землю
     * - Компас ставится в слот 8
     */
    public static void giveCompass(Player player) {
        ItemStack compass = createMineCompass();
        ItemStack existingItem = player.getInventory().getItem(8);

        if (existingItem != null && existingItem.getType() != Material.AIR) {
            // Слот 8 занят — перемещаем предмет в свободный слот
            int freeSlot = player.getInventory().firstEmpty();

            if (freeSlot != -1) {
                // Есть свободный слот — перемещаем туда
                player.getInventory().setItem(freeSlot, existingItem.clone());
            } else {
                // Инвентарь полон — дропаем на землю
                player.getWorld().dropItemNaturally(
                        player.getLocation(), existingItem.clone());
                player.sendMessage(Component.text("[Шахта] ")
                        .color(NamedTextColor.DARK_GREEN)
                        .append(Component.text("Инвентарь полон! Предмет из 9 слота выброшен.")
                                .color(NamedTextColor.YELLOW)));
            }
        }

        // Теперь ставим компас в слот 8
        player.getInventory().setItem(8, compass);
    }

    /**
     * Удалить все компасы шахтёра из инвентаря
     */
    public static void removeCompass(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isMineCompass(item)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    /**
     * ПКМ компасом — выход из шахты
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!isMineCompass(item)) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        if (!mineLevelGUI.isPlayerInMine(uuid)) return;

        performExit(player);
    }

    /**
     * Выполнить выход из шахты
     */
    public void performExit(Player player) {
        UUID uuid = player.getUniqueId();

        mineLevelGUI.removePlayerFromMine(uuid);
        plugin.getTaxTracker().reset(uuid);
        plugin.getFreezeManager().unfreeze(uuid);
        removeCompass(player);

        Location exitLocation = new Location(
                player.getWorld(),
                EXIT_X, EXIT_Y, EXIT_Z
        );
        exitLocation.setYaw(player.getLocation().getYaw());
        exitLocation.setPitch(player.getLocation().getPitch());
        player.teleport(exitLocation);

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("==============================")
                .color(NamedTextColor.GREEN));
        player.sendMessage(Component.text(" Вы вышли из шахты!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("==============================")
                .color(NamedTextColor.GREEN));
        player.sendMessage(Component.empty());
    }

    /**
     * Запрет выброса компаса
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isMineCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Нельзя выбросить компас шахтёра!")
                    .color(NamedTextColor.RED));
        }
    }

    /**
     * Запрет перемещения компаса в инвентаре
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (isMineCompass(current) || isMineCompass(cursor)) {
            if (event.getInventory().getHolder() instanceof
                    com.mine.plugin.gui.MineInventoryHolder) {
                return;
            }
            event.setCancelled(true);
        }
    }

    /**
     * При смерти — удаляем из шахтёров и убираем компас из дропа
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (mineLevelGUI.isPlayerInMine(uuid)) {
            event.getDrops().removeIf(CompassListener::isMineCompass);
            mineLevelGUI.removePlayerFromMine(uuid);
            plugin.getTaxTracker().reset(uuid);
        }
    }

    /**
     * При выходе с сервера
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (mineLevelGUI.isPlayerInMine(uuid)) {
            removeCompass(player);
            mineLevelGUI.removePlayerFromMine(uuid);
            plugin.getTaxTracker().reset(uuid);
        }
    }
}
