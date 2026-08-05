package com.mine.plugin.listeners;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.gui.MineLevelGUI;
import com.mine.plugin.managers.ConfigManager;
import com.mine.plugin.utils.TaxUtils;
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

public class CompassListener implements Listener {

    private final MinePlugin plugin;
    private final MineLevelGUI mineLevelGUI;
    public static final String COMPASS_NAME = "Выход из шахты";

    public CompassListener(MinePlugin plugin, MineLevelGUI mineLevelGUI) {
        this.plugin = plugin;
        this.mineLevelGUI = mineLevelGUI;
    }

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
                        .decoration(TextDecoration.ITALIC, false)
        ));
        compass.setItemMeta(meta);
        return compass;
    }

    public static boolean isMineCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        String name = net.kyori.adventure.text.serializer.plain
                .PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        return name.contains(COMPASS_NAME);
    }

    public static void giveCompass(Player player) {
        ItemStack compass = createMineCompass();
        ItemStack existing = player.getInventory().getItem(8);

        if (existing != null && existing.getType() != Material.AIR) {
            int freeSlot = player.getInventory().firstEmpty();
            if (freeSlot != -1) {
                player.getInventory().setItem(freeSlot, existing.clone());
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), existing.clone());
            }
        }
        player.getInventory().setItem(8, compass);
    }

    public static void removeCompass(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isMineCompass(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isMineCompass(event.getItem())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!mineLevelGUI.isPlayerInMine(player.getUniqueId())) return;

        performExit(player);
    }

    public void performExit(Player player) {
        UUID uuid = player.getUniqueId();
        mineLevelGUI.removePlayerFromMine(uuid);
        plugin.getTaxTracker().reset(uuid);
        plugin.getFreezeManager().unfreeze(uuid);
        removeCompass(player);

        ConfigManager cfg = plugin.getConfigManager();
        Location exitLoc = new Location(player.getWorld(),
                cfg.getEntryX(), cfg.getEntryY(), cfg.getEntryZ());
        exitLoc.setYaw(player.getLocation().getYaw());
        exitLoc.setPitch(player.getLocation().getPitch());
        player.teleport(exitLoc);

        TaxUtils.playTeleportSound(player);
        player.sendMessage(Component.text(" Вы вышли из шахты!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isMineCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (isMineCompass(event.getCurrentItem()) || isMineCompass(event.getCursor())) {
            if (event.getInventory().getHolder() instanceof
                    com.mine.plugin.gui.MineInventoryHolder) return;
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (mineLevelGUI.isPlayerInMine(player.getUniqueId())) {
            event.getDrops().removeIf(CompassListener::isMineCompass);
            mineLevelGUI.removePlayerFromMine(player.getUniqueId());
            plugin.getTaxTracker().reset(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (mineLevelGUI.isPlayerInMine(player.getUniqueId())) {
            removeCompass(player);
            mineLevelGUI.removePlayerFromMine(player.getUniqueId());
            plugin.getTaxTracker().reset(player.getUniqueId());
        }
    }
}
