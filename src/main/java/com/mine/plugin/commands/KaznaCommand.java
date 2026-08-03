package com.mine.plugin.commands;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.gui.MineInventoryHolder;
import com.mine.plugin.managers.KaznaManager;
import com.mine.plugin.utils.TaxUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class KaznaCommand implements CommandExecutor, TabCompleter, Listener {

    private final MinePlugin plugin;

    public KaznaCommand(MinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Эта команда только для игроков!");
            return true;
        }

        if (!player.hasPermission("mine.kazna")) {
            player.sendMessage(Component.text("У вас нет доступа к казне!")
                    .color(NamedTextColor.RED));
            return true;
        }

        openKaznaGUI(player);
        return true;
    }

    private void openKaznaGUI(Player player) {
        KaznaManager kazna = plugin.getKaznaManager();
        Map<Material, Integer> items = kazna.getAllItems();

        // Рассчитываем размер GUI
        int neededSlots = items.size() + 9; // +9 для верхнего ряда
        int rows = (int) Math.ceil(neededSlots / 9.0);
        rows = Math.max(2, Math.min(6, rows)); // от 2 до 6 рядов
        int size = rows * 9;

        MineInventoryHolder holder = new MineInventoryHolder(
                MineInventoryHolder.GUIType.KAZNA_VIEW);

        Inventory gui = Bukkit.createInventory(holder, size,
                Component.text("Казна города")
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, true));

        // === Верхний ряд: декорация ===
        ItemStack glass = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glass.setItemMeta(glassMeta);

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, glass);
        }

        // === Заголовок (слот 4) ===
        ItemStack header = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta headerMeta = header.getItemMeta();
        headerMeta.displayName(Component.text("КАЗНА ГОСУДАРСТВА")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> headerLore = new ArrayList<>();
        headerLore.add(Component.empty());
        headerLore.add(Component.text("Здесь хранятся все налоги")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.text("и конфискованные ресурсы")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.empty());
        headerLore.add(Component.text("Всего ресурсов: " + kazna.getTotalItemCount())
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.text("Типов: " + items.size())
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

        headerMeta.lore(headerLore);
        header.setItemMeta(headerMeta);
        gui.setItem(4, header);

        // === Предметы казны ===
        if (items.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.displayName(Component.text("Казна пуста")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> emptyLore = new ArrayList<>();
            emptyLore.add(Component.empty());
            emptyLore.add(Component.text("Ресурсы появятся после")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            emptyLore.add(Component.text("сбора налогов в шахте")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            emptyMeta.lore(emptyLore);
            empty.setItemMeta(emptyMeta);
            gui.setItem(13, empty);
        } else {
            int slot = 9;
            for (Map.Entry<Material, Integer> entry : items.entrySet()) {
                if (slot >= size) break;

                int displayAmount = Math.min(entry.getValue(), 64);
                ItemStack item = new ItemStack(entry.getKey(), displayAmount);
                ItemMeta meta = item.getItemMeta();

                meta.displayName(
                        Component.text(TaxUtils.getRussianName(entry.getKey()))
                                .color(NamedTextColor.WHITE)
                                .decoration(TextDecoration.ITALIC, false));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("Количество: " + entry.getValue() + " шт.")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));

                int stacks = entry.getValue() / 64;
                int remainder = entry.getValue() % 64;
                if (stacks > 0) {
                    lore.add(Component.text("= " + stacks + " стак(ов) + " + remainder + " шт.")
                            .color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                }

                meta.lore(lore);
                item.setItemMeta(meta);
                gui.setItem(slot, item);
                slot++;
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getInventory().getHolder() == null) return;
        if (!(event.getInventory().getHolder() instanceof MineInventoryHolder holder)) return;

        if (holder.getType() == MineInventoryHolder.GUIType.KAZNA_VIEW) {
            event.setCancelled(true); // Нельзя забирать из казны
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                  @NotNull Command command,
                                                  @NotNull String alias,
                                                  @NotNull String[] args) {
        return Collections.emptyList();
    }
}
