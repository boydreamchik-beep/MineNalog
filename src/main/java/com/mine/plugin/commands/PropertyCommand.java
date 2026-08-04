package com.mine.plugin.commands;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.gui.MineInventoryHolder;
import com.mine.plugin.managers.*;
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

public class PropertyCommand implements CommandExecutor, TabCompleter, Listener {

    private final MinePlugin plugin;

    public PropertyCommand(MinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }
        openPropertyMenu(player);
        return true;
    }

    public void openPropertyMenu(Player player) {
        MineInventoryHolder holder = new MineInventoryHolder(MineInventoryHolder.GUIType.PROPERTY_MENU);
        Inventory gui = Bukkit.createInventory(holder, 27,
                Component.text("Имущество").color(NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.BOLD, true));

        ItemStack glass = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.displayName(Component.text(" "));
        glass.setItemMeta(gm);
        for (int i = 0; i < 27; i++) gui.setItem(i, glass);

        UUID uuid = player.getUniqueId();
        int totalCobble = ChestScanner.countTotalMaterial(player, Material.COBBLESTONE);

        // Заголовок
        ItemStack info = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("ИМУЩЕСТВО").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        im.lore(List.of(
                Component.empty(),
                Component.text("Ваш баланс: " + totalCobble + " булыж.")
                        .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(im);
        gui.setItem(4, info);

        // Квартира вторичка (в разработке)
        ItemStack apt1 = new ItemStack(Material.BARRIER);
        ItemMeta apt1m = apt1.getItemMeta();
        apt1m.displayName(Component.text("Квартира (вторичка)").color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        apt1m.lore(List.of(Component.text("В разработке...").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        apt1.setItemMeta(apt1m);
        gui.setItem(10, apt1);

        // Квартира новостройка (в разработке)
        ItemStack apt2 = new ItemStack(Material.BARRIER);
        ItemMeta apt2m = apt2.getItemMeta();
        apt2m.displayName(Component.text("Квартира (новостройка)").color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        apt2m.lore(List.of(Component.text("В разработке...").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        apt2.setItemMeta(apt2m);
        gui.setItem(12, apt2);

        // Участок земли
        boolean hasPlot = plugin.getPropertyManager().hasPlot(uuid, "plot-1");
        ItemStack plot = new ItemStack(hasPlot ? Material.LIME_CONCRETE : Material.GRASS_BLOCK);
        ItemMeta pm = plot.getItemMeta();

        if (hasPlot) {
            pm.displayName(Component.text("Участок №1 (Куплен!)").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
            pm.lore(List.of(
                    Component.empty(),
                    Component.text("Вы уже владеете этим участком").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        } else {
            int price = plugin.getConfig().getInt("property.plots.plot-1.price-per-block", 32);
            int blocks = plugin.getConfig().getInt("property.plots.plot-1.surface-blocks", 1);
            int totalPrice = price * blocks;

            pm.displayName(Component.text("Участок №1").color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Размер: " + blocks + " блок (3 вниз + 20 вверх)")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Цена: " + totalPrice + " булыжников")
                    .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            if (totalCobble >= totalPrice) {
                lore.add(Component.text("ЛКМ — купить за полную стоимость")
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Не хватает " + (totalPrice - totalCobble))
                        .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("ПКМ — оформить рассрочку")
                    .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));

            pm.lore(lore);
        }

        plot.setItemMeta(pm);
        gui.setItem(14, plot);

        // Моя собственность
        ItemStack myProp = new ItemStack(Material.BOOK);
        ItemMeta myPm = myProp.getItemMeta();
        List<PropertyManager.OwnedPlot> owned = plugin.getPropertyManager().getOwnedPlots(uuid);
        myPm.displayName(Component.text("Моя собственность").color(NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));

        List<Component> myLore = new ArrayList<>();
        myLore.add(Component.empty());
        if (owned.isEmpty()) {
            myLore.add(Component.text("У вас нет имущества").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            for (PropertyManager.OwnedPlot op : owned) {
                String status = op.paidFull ? "§aОплачен" : "§eРассрочка";
                myLore.add(Component.text("• " + op.plotId + " " + status)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        myPm.lore(myLore);
        myProp.setItemMeta(myPm);
        gui.setItem(16, myProp);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof MineInventoryHolder holder)) return;

        if (holder.getType() == MineInventoryHolder.GUIType.PROPERTY_MENU) {
            event.setCancelled(true);

            if (event.getRawSlot() == 14) {
                UUID uuid = player.getUniqueId();

                if (plugin.getPropertyManager().hasPlot(uuid, "plot-1")) {
                    player.sendMessage(Component.text("[Имущество] Вы уже владеете этим участком!")
                            .color(NamedTextColor.RED));
                    return;
                }

                int price = plugin.getConfig().getInt("property.plots.plot-1.price-per-block", 32);
                int blocks = plugin.getConfig().getInt("property.plots.plot-1.surface-blocks", 1);
                int totalPrice = price * blocks;

                if (event.isLeftClick()) {
                    // Покупка за полную стоимость
                    boolean success = plugin.getPropertyManager()
                            .buyPlotFull(uuid, "plot-1", totalPrice, player);

                    if (success) {
                        player.closeInventory();
                        player.sendMessage(Component.text("[Имущество] Участок №1 куплен!")
                                .color(NamedTextColor.GREEN));
                        // Обновить место жительства
                        plugin.getPassportManager().updateResidence(uuid, "Участок №1");
                    } else {
                        player.sendMessage(Component.text("[Имущество] Не хватает булыжника!")
                                .color(NamedTextColor.RED));
                    }

                } else if (event.isRightClick()) {
                    // Рассрочка
                    if (plugin.getPropertyManager().hasAnyInstallment(uuid)) {
                        player.sendMessage(Component.text("[Имущество] У вас уже есть рассрочка!")
                                .color(NamedTextColor.RED));
                        return;
                    }

                    var result = plugin.getPropertyManager()
                            .evaluateInstallment(uuid, "plot-1", totalPrice, player);

                    if (result.approved) {
                        plugin.getPropertyManager()
                                .createInstallment(uuid, "plot-1", totalPrice, result.termDays);
                        player.closeInventory();
                        plugin.getPassportManager().updateResidence(uuid, "Участок №1 (рассрочка)");

                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("╔══════════════════════════════╗")
                                .color(NamedTextColor.GREEN));
                        player.sendMessage(Component.text("║  РАССРОЧКА ОДОБРЕНА!")
                                .color(NamedTextColor.GREEN)
                                .decoration(TextDecoration.BOLD, true));
                        player.sendMessage(Component.text("║  Участок: №1")
                                .color(NamedTextColor.WHITE));
                        player.sendMessage(Component.text("║  Стоимость: " + totalPrice)
                                .color(NamedTextColor.YELLOW));
                        player.sendMessage(Component.text("║  Срок: " + result.termDays + " игр. дней")
                                .color(NamedTextColor.AQUA));
                        player.sendMessage(Component.text("║  Просрочка: +3%/день")
                                .color(NamedTextColor.RED));
                        player.sendMessage(Component.text("╚══════════════════════════════╝")
                                .color(NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("[Имущество] Рассрочка отклонена: "
                                        + result.message)
                                .color(NamedTextColor.RED));
                    }
                }
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
