package com.mine.plugin.commands;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.gui.MineInventoryHolder;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Магазин ресурсов за булыжник.
 * Команда: /buy
 * 
 * Товары:
 * - Дубовое бревно: 32 булыжника
 * - Свинина (сырая): 24 булыжника
 * - Уголь: 16 булыжников
 * - Железная руда: 64 булыжника
 * - Алмаз: 256 булыжников (4 стака)
 */
public class ShopCommand implements CommandExecutor, TabCompleter, Listener {

    private final MinePlugin plugin;

    // Товары: Material -> цена в булыжниках
    private static final LinkedHashMap<Material, ShopItem> SHOP_ITEMS = new LinkedHashMap<>();

    static {
        SHOP_ITEMS.put(Material.OAK_LOG, new ShopItem(
                "Дубовое бревно", Material.OAK_LOG, 32,
                "Отличный строительный материал"
        ));
        SHOP_ITEMS.put(Material.PORKCHOP, new ShopItem(
                "Свинина (сырая)", Material.PORKCHOP, 24,
                "Пожарь и утоли голод"
        ));
        SHOP_ITEMS.put(Material.COAL, new ShopItem(
                "Уголь", Material.COAL, 16,
                "Топливо для печей"
        ));
        SHOP_ITEMS.put(Material.RAW_IRON, new ShopItem(
                "Железная руда (сырое железо)", Material.RAW_IRON, 64,
                "Переплавь в слиток"
        ));
        SHOP_ITEMS.put(Material.DIAMOND, new ShopItem(
                "Алмаз", Material.DIAMOND, 256,
                "Редкий и дорогой ресурс"
        ));
    }

    public ShopCommand(MinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Эта команда только для игроков!");
            return true;
        }

        openShopGUI(player);
        return true;
    }

    private void openShopGUI(Player player) {
        MineInventoryHolder holder = new MineInventoryHolder(
                MineInventoryHolder.GUIType.SHOP);

        Inventory gui = Bukkit.createInventory(holder, 36,
                Component.text("Магазин ресурсов")
                        .color(NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.BOLD, true));

        // Декоративное стекло
        ItemStack glass = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.displayName(Component.text(" "));
        glass.setItemMeta(glassMeta);

        for (int i = 0; i < 36; i++) {
            gui.setItem(i, glass);
        }

        // Заголовок (слот 4)
        ItemStack header = new ItemStack(Material.EMERALD);
        ItemMeta headerMeta = header.getItemMeta();
        headerMeta.displayName(Component.text("МАГАЗИН")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> headerLore = new ArrayList<>();
        headerLore.add(Component.empty());
        headerLore.add(Component.text("Валюта: Булыжник")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        // Считаем булыжник в инвентаре
        int playerCobble = countCobblestone(player);
        headerLore.add(Component.text("Ваш баланс: " + playerCobble + " шт.")
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

        headerMeta.lore(headerLore);
        header.setItemMeta(headerMeta);
        gui.setItem(4, header);

        // Товары
        int[] slots = {19, 20, 21, 23, 24}; // Центральный ряд
        int index = 0;

        for (Map.Entry<Material, ShopItem> entry : SHOP_ITEMS.entrySet()) {
            if (index >= slots.length) break;

            ShopItem shopItem = entry.getValue();
            ItemStack item = new ItemStack(shopItem.material);
            ItemMeta meta = item.getItemMeta();

            meta.displayName(Component.text(shopItem.name)
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text(shopItem.description)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Цена: " + shopItem.price + " булыжников")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());

            // Показываем может ли игрок купить
            if (playerCobble >= shopItem.price) {
                lore.add(Component.text("✔ Нажмите чтобы купить!")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("✖ Не хватает " + (shopItem.price - playerCobble) + " булыжников")
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            gui.setItem(slots[index], item);
            index++;
        }

        // Информация о валюте (слот 31)
        ItemStack cobbleInfo = new ItemStack(Material.COBBLESTONE);
        ItemMeta cobbleMeta = cobbleInfo.getItemMeta();
        cobbleMeta.displayName(Component.text("Валюта: Булыжник")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> cobbleLore = new ArrayList<>();
        cobbleLore.add(Component.empty());
        cobbleLore.add(Component.text("Добывайте в шахте!")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        cobbleLore.add(Component.text("Ваш баланс: " + playerCobble + " шт.")
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        cobbleMeta.lore(cobbleLore);
        cobbleInfo.setItemMeta(cobbleMeta);
        gui.setItem(31, cobbleInfo);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getHolder() == null) return;
        if (!(event.getInventory().getHolder() instanceof MineInventoryHolder holder)) return;
        if (holder.getType() != MineInventoryHolder.GUIType.SHOP) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        int[] shopSlots = {19, 20, 21, 23, 24};

        // Определяем какой товар
        int shopIndex = -1;
        for (int i = 0; i < shopSlots.length; i++) {
            if (shopSlots[i] == slot) {
                shopIndex = i;
                break;
            }
        }

        if (shopIndex == -1) return;

        // Получаем товар
        List<ShopItem> items = new ArrayList<>(SHOP_ITEMS.values());
        if (shopIndex >= items.size()) return;

        ShopItem shopItem = items.get(shopIndex);
        int playerCobble = countCobblestone(player);

        if (playerCobble < shopItem.price) {
            player.sendMessage(Component.text("[Магазин] ")
                    .color(NamedTextColor.DARK_GREEN)
                    .append(Component.text("Не хватает булыжников! Нужно: "
                                    + shopItem.price + ", у вас: " + playerCobble)
                            .color(NamedTextColor.RED)));
            return;
        }

        // Списываем булыжник
        removeCobblestone(player, shopItem.price);

        // Выдаём товар
        Map<Integer, ItemStack> overflow = player.getInventory()
                .addItem(new ItemStack(shopItem.material, 1));

        // Если инвентарь полон
        if (!overflow.isEmpty()) {
            for (ItemStack overflowItem : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
            }
        }

        player.sendMessage(Component.text("[Магазин] ")
                .color(NamedTextColor.DARK_GREEN)
                .append(Component.text("Куплено: " + shopItem.name
                                + " за " + shopItem.price + " булыжников!")
                        .color(NamedTextColor.GREEN)));

        // Обновляем GUI
        player.closeInventory();
        openShopGUI(player);
    }

    /**
     * Считает количество булыжника в инвентаре
     */
    private int countCobblestone(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COBBLESTONE) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Удаляет нужное количество булыжника из инвентаря
     */
    private void removeCobblestone(Player player, int amount) {
        int remaining = amount;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != Material.COBBLESTONE) continue;

            // Пропускаем компас шахтёра
            if (com.mine.plugin.listeners.CompassListener.isMineCompass(item)) continue;

            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }

            if (remaining <= 0) break;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                  @NotNull Command command,
                                                  @NotNull String alias,
                                                  @NotNull String[] args) {
        return Collections.emptyList();
    }

    /**
     * Класс товара
     */
    private static class ShopItem {
        final String name;
        final Material material;
        final int price;
        final String description;

        ShopItem(String name, Material material, int price, String description) {
            this.name = name;
            this.material = material;
            this.price = price;
            this.description = description;
        }
    }
}
