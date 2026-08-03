package com.mine.plugin.gui;

import com.mine.plugin.MinePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MineLevelGUI implements Listener {

    private final MinePlugin plugin;
    private final Set<UUID> playersInMine = new HashSet<>();

    public MineLevelGUI(MinePlugin plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        MineInventoryHolder holder = new MineInventoryHolder(
                MineInventoryHolder.GUIType.MINE_LEVEL_SELECT);

        Inventory gui = Bukkit.createInventory(holder, 27,
                Component.text("Выбор уровня шахты")
                        .color(NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.BOLD, true));

        // === Декоративное стекло ===
        ItemStack glass = createGlassPane();
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glass);
        }

        // === Заголовок (слот 4) ===
        ItemStack info = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("ГОСУДАРСТВЕННАЯ ШАХТА")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.empty());
        infoLore.add(Component.text("Выберите уровень шахты")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        // === Уровень 1 (слот 11) ===
        ItemStack level1 = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta meta1 = level1.getItemMeta();
        meta1.displayName(Component.text("Уровень 1")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore1 = new ArrayList<>();
        lore1.add(Component.empty());
        lore1.add(Component.text("Высота: Y = 43")
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        lore1.add(Component.text("Налог: 20% от добычи")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        lore1.add(Component.empty());
        lore1.add(Component.text("Правила:")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        lore1.add(Component.text("- Булыжник, диорит, андезит")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore1.add(Component.text("  остаются как есть")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore1.add(Component.text("- Прочие блоки превращаются")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore1.add(Component.text("  в булыжник")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore1.add(Component.text("- Ценные руды полностью")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore1.add(Component.text("  уходят в казну города")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore1.add(Component.empty());
        lore1.add(Component.text("Нажмите для телепортации!")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, true));

        meta1.lore(lore1);
        level1.setItemMeta(meta1);
        gui.setItem(11, level1);

        // === Уровень 2 (слот 15) ===
        ItemStack level2 = new ItemStack(Material.BARRIER);
        ItemMeta meta2 = level2.getItemMeta();
        meta2.displayName(Component.text("Уровень 2")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore2 = new ArrayList<>();
        lore2.add(Component.empty());
        lore2.add(Component.text("В разработке...")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore2.add(Component.empty());
        lore2.add(Component.text("Скоро будет доступен!")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta2.lore(lore2);
        level2.setItemMeta(meta2);
        gui.setItem(15, level2);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getHolder() == null) return;
        if (!(event.getInventory().getHolder() instanceof MineInventoryHolder holder)) return;
        if (holder.getType() != MineInventoryHolder.GUIType.MINE_LEVEL_SELECT) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (slot == 11) {
            // === Уровень 1 ===
            player.closeInventory();

            Location mineLocation = new Location(
                    player.getWorld(),
                    -229.601, 43.0, -70.282
            );
            mineLocation.setYaw(player.getLocation().getYaw());
            mineLocation.setPitch(player.getLocation().getPitch());
            player.teleport(mineLocation);

            playersInMine.add(player.getUniqueId());

            // Сбрасываем счётчик налогов
            plugin.getTaxTracker().reset(player.getUniqueId());

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("==============================")
                    .color(NamedTextColor.DARK_GREEN));
            player.sendMessage(Component.text(" Вы спустились на 1 уровень шахты!")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true));
            player.sendMessage(Component.text(" Высота: Y = 43")
                    .color(NamedTextColor.GRAY));
            player.sendMessage(Component.text(" Налог: каждый 5-й блок (20%)")
                    .color(NamedTextColor.RED));
            player.sendMessage(Component.text(" Ценные руды -> казна города")
                    .color(NamedTextColor.GOLD));
            player.sendMessage(Component.text(" Прочие блоки -> булыжник")
                    .color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("==============================")
                    .color(NamedTextColor.DARK_GREEN));
            player.sendMessage(Component.empty());

        } else if (slot == 15) {
            // === Уровень 2 ===
            player.sendMessage(Component.text("Уровень 2 находится в разработке!")
                    .color(NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playersInMine.remove(uuid);
        plugin.getTaxTracker().reset(uuid);
    }

    public boolean isPlayerInMine(UUID uuid) {
        return playersInMine.contains(uuid);
    }

    public void removePlayerFromMine(UUID uuid) {
        playersInMine.remove(uuid);
    }

    public Set<UUID> getPlayersInMine() {
        return playersInMine;
    }

    private ItemStack createGlassPane() {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);
        return glass;
    }
}
