package com.mine.plugin.commands;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.gui.MineInventoryHolder;
import com.mine.plugin.managers.ConfigManager;
import com.mine.plugin.managers.ConfigManager.ShopItemConfig;
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

import java.util.*;

public class ShopCommand implements CommandExecutor, TabCompleter, Listener {

    private final MinePlugin plugin;

    public ShopCommand(MinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }
        openShopGUI(player);
        return true;
    }

    private void openShopGUI(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        List<ShopItemConfig> items = cfg.getShopItems();
        Material currency = cfg.getShopCurrency();
        String currencyName = cfg.getShopCurrencyName();

        int rows = Math.max(3, (int) Math.ceil((items.size() + 9) / 9.0) + 1);
        rows = Math.min(rows, 6);
        int size = rows * 9;

        MineInventoryHolder holder = new MineInventoryHolder(MineInventoryHolder.GUIType.SHOP);
        Inventory gui = Bukkit.createInventory(holder, size,
                Component.text("Магазин ресурсов")
                        .color(NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.BOLD, true));

        ItemStack glass = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.displayName(Component.text(" "));
        glass.setItemMeta(gm);
        for (int i = 0; i < size; i++) gui.setItem(i, glass);

        int playerCurrency = countMaterial(player, currency);

        // Заголовок
        ItemStack header = new ItemStack(Material.EMERALD);
        ItemMeta hm = header.getItemMeta();
        hm.displayName(Component.text("МАГАЗИН").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        hm.lore(List.of(
                Component.empty(),
                Component.text("Валюта: " + currencyName)
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Баланс: " + playerCurrency + " шт.")
                        .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
        ));
        header.setItemMeta(hm);
        gui.setItem(4, header);

        // Товары
        int slot = 9;
        for (ShopItemConfig shopItem : items) {
            if (slot >= size) break;

            ItemStack item = new ItemStack(shopItem.material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(shopItem.name)
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text(shopItem.description)
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Цена: " + shopItem.price + " " + currencyName)
                    .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());

            if (playerCurrency >= shopItem.price) {
                lore.add(Component.text("✔ Нажмите чтобы купить!")
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("✖ Не хватает " + (shopItem.price - playerCurrency))
                        .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            gui.setItem(slot, item);
            slot++;
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof MineInventoryHolder holder)) return;
        if (holder.getType() != MineInventoryHolder.GUIType.SHOP) return;

        event.setCancelled(true);

        ConfigManager cfg = plugin.getConfigManager();
        List<ShopItemConfig> items = cfg.getShopItems();
        Material currency = cfg.getShopCurrency();

        int slot = event.getRawSlot();
        int itemIndex = slot - 9;

        if (itemIndex < 0 || itemIndex >= items.size()) return;

        ShopItemConfig shopItem = items.get(itemIndex);
        int playerCurrency = countMaterial(player, currency);

        if (playerCurrency < shopItem.price) {
            player.sendMessage(Component.text("[Магазин] Не хватает " + cfg.getShopCurrencyName() + "!")
                    .color(NamedTextColor.RED));
            return;
        }

        removeMaterial(player, currency, shopItem.price);

        int buyAmount = Math.max(1, shopItem.amount);
        Map<Integer, ItemStack> overflow = player.getInventory()
                .addItem(new ItemStack(shopItem.material, buyAmount));
        for (ItemStack item : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }

        player.sendMessage(Component.text("[Магазин] Куплено: " + shopItem.name
                        + " x" + buyAmount + " за " + shopItem.price + " " + cfg.getShopCurrencyName())
                .color(NamedTextColor.GREEN));

        player.closeInventory();
        openShopGUI(player);
    }

    private int countMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != material) continue;
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
}
