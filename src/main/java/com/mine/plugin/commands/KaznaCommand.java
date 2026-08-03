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

/**
 * ИЗМЕНЕНИЯ:
 * - Размер = 54 (большой сундук)
 * - 100 страниц с навигацией
 * - Нижний ряд (45-53) = навигация
 */
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

        openKaznaGUI(player, 0);
        return true;
    }

    public void openKaznaGUI(Player player, int page) {
        KaznaManager kazna = plugin.getKaznaManager();

        int maxPages = kazna.getMaxPages();
        if (page < 0) page = 0;
        if (page >= maxPages) page = maxPages - 1;

        MineInventoryHolder holder = new MineInventoryHolder(
                MineInventoryHolder.GUIType.KAZNA_PAGED, page);

        Inventory gui = Bukkit.createInventory(holder, 54,
                Component.text("Казна города [" + (page + 1) + "/" + maxPages + "]")
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, true));

        // === Верхний ряд: декорация (0-8) ===
        ItemStack topGlass = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta topGlassMeta = topGlass.getItemMeta();
        topGlassMeta.displayName(Component.text(" "));
        topGlass.setItemMeta(topGlassMeta);

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, topGlass);
        }

        // Заголовок (слот 4)
        ItemStack header = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta headerMeta = header.getItemMeta();
        headerMeta.displayName(Component.text("КАЗНА ГОСУДАРСТВА")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> headerLore = new ArrayList<>();
        headerLore.add(Component.empty());
        headerLore.add(Component.text("Всего ресурсов: " + kazna.getTotalItemCount())
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.text("Страница: " + (page + 1) + " / " + maxPages)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.text("Макс. страниц: " + KaznaManager.MAX_PAGES)
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        headerMeta.lore(headerLore);
        header.setItemMeta(headerMeta);
        gui.setItem(4, header);

        // === Предметы казны (слоты 9-53, кроме нижнего ряда 45-53) ===
        Map<Material, Integer> pageItems = kazna.getItemsForPage(page);

        if (pageItems.isEmpty() && page == 0) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.displayName(Component.text("Казна пуста")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> emptyLore = new ArrayList<>();
            emptyLore.add(Component.text("Ресурсы появятся после")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            emptyLore.add(Component.text("сбора налогов в шахте")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            emptyMeta.lore(emptyLore);
            empty.setItemMeta(emptyMeta);
            gui.setItem(22, empty);
        } else {
            int slot = 9;
            for (Map.Entry<Material, Integer> entry : pageItems.entrySet()) {
                if (slot >= 45) break; // Оставляем нижний ряд для навигации

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

        // === Нижний ряд: навигация (45-53) ===
        ItemStack navGlass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta navGlassMeta = navGlass.getItemMeta();
        navGlassMeta.displayName(Component.text(" "));
        navGlass.setItemMeta(navGlassMeta);

        for (int i = 45; i < 54; i++) {
            gui.setItem(i, navGlass);
        }

        // Кнопка "Назад" (слот 45)
        if (page > 0) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevBtn.getItemMeta();
            prevMeta.displayName(Component.text("◀ Назад")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> prevLore = new ArrayList<>();
            prevLore.add(Component.text("Страница " + page)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            prevMeta.lore(prevLore);
            prevBtn.setItemMeta(prevMeta);
            gui.setItem(45, prevBtn);
        }

        // Информация о странице (слот 49)
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        pageInfoMeta.displayName(Component.text("Страница " + (page + 1) + " / " + maxPages)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        pageInfo.setItemMeta(pageInfoMeta);
        gui.setItem(49, pageInfo);

        // Кнопка "Вперёд" (слот 53)
        if (page < maxPages - 1) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextBtn.getItemMeta();
            nextMeta.displayName(Component.text("Вперёд ▶")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> nextLore = new ArrayList<>();
            nextLore.add(Component.text("Страница " + (page + 2))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            nextMeta.lore(nextLore);
            nextBtn.setItemMeta(nextMeta);
            gui.setItem(53, nextBtn);
        }

        // Кнопка "Закрыть" (слот 49 заменяем на 50 нет, оставим 49 как инфо)
        // Закрытие через обычный ESC

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getHolder() == null) return;
        if (!(event.getInventory().getHolder() instanceof MineInventoryHolder holder)) return;

        // Старый тип (обратная совместимость)
        if (holder.getType() == MineInventoryHolder.GUIType.KAZNA_VIEW) {
            event.setCancelled(true);
            return;
        }

        if (holder.getType() != MineInventoryHolder.GUIType.KAZNA_PAGED) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        int currentPage = holder.getPage();

        // Кнопка "Назад"
        if (slot == 45 && currentPage > 0) {
            openKaznaGUI(player, currentPage - 1);
            return;
        }

        // Кнопка "Вперёд"
        if (slot == 53) {
            int maxPages = plugin.getKaznaManager().getMaxPages();
            if (currentPage < maxPages - 1) {
                openKaznaGUI(player, currentPage + 1);
            }
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
