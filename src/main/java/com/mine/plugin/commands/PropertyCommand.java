package com.mine.plugin.commands;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.gui.MineInventoryHolder;
import com.mine.plugin.managers.*;
import com.mine.plugin.utils.TaxUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * /property           — открыть меню имущества
 * /property pay <сумма> — оплатить рассрочку
 * /property pay all    — оплатить всю рассрочку
 * /property info       — информация о рассрочке
 * /property tax        — оплатить земельный налог
 */
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

        if (args.length == 0) {
            openPropertyMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "pay" -> handlePay(player, args);
            case "info" -> handleInfo(player);
            case "tax" -> handleTax(player);
            default -> openPropertyMenu(player);
        }

        return true;
    }

    // === ОПЛАТА РАССРОЧКИ ===

    private void handlePay(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("[Рассрочка] /property pay <сумма> или /property pay all")
                    .color(NamedTextColor.RED));
            return;
        }

        PropertyManager pm = plugin.getPropertyManager();
        UUID uuid = player.getUniqueId();

        if (!pm.hasAnyInstallment(uuid)) {
            player.sendMessage(Component.text("[Рассрочка] У вас нет активной рассрочки!")
                    .color(NamedTextColor.GREEN));
            return;
        }

        int amount;

        if (args[1].equalsIgnoreCase("all")) {
            PropertyManager.InstallmentData allData = pm.getInstallment(uuid);
            amount = allData != null ? allData.remaining : 0;
            if (amount <= 0) {
                player.sendMessage(Component.text("[Рассрочка] У вас нет активной рассрочки!")
                        .color(NamedTextColor.GREEN));
                return;
            }
        } else {
            try {
                if (args[1].toLowerCase().endsWith("s")) {
                    int stacks = Integer.parseInt(args[1].substring(0, args[1].length() - 1));
                    amount = stacks * 64;
                } else {
                    amount = Integer.parseInt(args[1]);
                }
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("[Рассрочка] Неверная сумма!")
                        .color(NamedTextColor.RED));
                return;
            }
        }

        PropertyManager.PayInstallmentResult result = pm.payInstallment(player, amount);

        switch (result) {
            case SUCCESS -> {
                PropertyManager.InstallmentData data = pm.getInstallment(uuid);

                if (data == null) {
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("╔══════════════════════════════╗")
                            .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.text("║  РАССРОЧКА ПОЛНОСТЬЮ ПОГАШЕНА!")
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.BOLD, true));
                    player.sendMessage(Component.text("║  Участок теперь полностью ваш!")
                            .color(NamedTextColor.WHITE));
                    player.sendMessage(Component.text("╚══════════════════════════════╝")
                            .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.empty());

                    plugin.getPassportManager().updateResidence(uuid, "Участок №1");
                } else {
                    player.sendMessage(Component.text("[Рассрочка] Оплачено! Остаток: "
                                    + data.remaining + " булыжников")
                            .color(NamedTextColor.GREEN));
                }
            }
            case NOT_ENOUGH -> {
                player.sendMessage(Component.text("[Рассрочка] Не хватает булыжника!")
                        .color(NamedTextColor.RED));
                int cobbleInv = 0;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.COBBLESTONE) {
                        cobbleInv += item.getAmount();
                    }
                }
                int cobbleContainers = ChestScanner.countInNearbyContainers(player, Material.COBBLESTONE);
                player.sendMessage(Component.text("[Рассрочка] У вас: " + (cobbleInv + cobbleContainers)
                                + " (инвентарь: " + cobbleInv + ", сундуки: " + cobbleContainers + ")")
                        .color(NamedTextColor.GRAY));
            }
            case NO_INSTALLMENT -> {
                player.sendMessage(Component.text("[Рассрочка] У вас нет рассрочки!")
                        .color(NamedTextColor.GREEN));
            }
            case INVALID_AMOUNT -> {
                player.sendMessage(Component.text("[Рассрочка] Неверная сумма!")
                        .color(NamedTextColor.RED));
            }
        }
    }

    // === ИНФОРМАЦИЯ О РАССРОЧКЕ ===

    private void handleInfo(Player player) {
        PropertyManager pm = plugin.getPropertyManager();
        UUID uuid = player.getUniqueId();

        if (!pm.hasAnyInstallment(uuid)) {
            player.sendMessage(Component.text("[Рассрочка] У вас нет активной рассрочки.")
                    .color(NamedTextColor.GREEN));
            return;
        }

        PropertyManager.InstallmentData data = pm.getInstallment(uuid);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        long now = System.currentTimeMillis();
        boolean overdue = now > data.dueDate;

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("╔══════════════════════════════╗")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  ИНФОРМАЦИЯ О РАССРОЧКЕ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("║")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  Участок: " + data.plotId)
                .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("║  Координаты: " + pm.getPlotCoordinates(data.plotId))
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("║  Стоимость: " + data.totalCost + " булыж.")
                .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("║  Оплачено: " + (data.totalCost - data.remaining))
                .color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("║  Остаток: " + data.remaining + " булыж.")
                .color(NamedTextColor.RED));
        player.sendMessage(Component.text("║  Срок: " + data.termDays + " игр. дней")
                .color(NamedTextColor.AQUA));
        player.sendMessage(Component.text("║  Начало: " + sdf.format(new Date(data.startDate)))
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("║  Срок до: " + sdf.format(new Date(data.dueDate)))
                .color(overdue ? NamedTextColor.RED : NamedTextColor.GRAY));

        if (overdue) {
            player.sendMessage(Component.text("║  ⚠ ПРОСРОЧЕНА! +3%/игр.день")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true));
        } else {
            long remaining = data.dueDate - now;
            long minutes = remaining / (1000 * 60);
            long hours = minutes / 60;
            player.sendMessage(Component.text("║  Осталось: " + hours + "ч " + (minutes % 60) + "мин")
                    .color(NamedTextColor.GREEN));
        }

        player.sendMessage(Component.text("║")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  /property pay <сумма>")
                .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("║  /property pay all")
                .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("╚══════════════════════════════╝")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
    }

    // === ЗЕМЕЛЬНЫЙ НАЛОГ ===

    private void handleTax(Player player) {
        PropertyManager pm = plugin.getPropertyManager();
        UUID uuid = player.getUniqueId();

        if (!pm.hasAnyPlot(uuid)) {
            player.sendMessage(Component.text("[Налог] У вас нет земельного участка!")
                    .color(NamedTextColor.RED));
            return;
        }

        boolean success = pm.payLandTax(player);

        if (success) {
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("╔══════════════════════════════╗")
                    .color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("║  ЗЕМЕЛЬНЫЙ НАЛОГ ОПЛАЧЕН!")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true));
            player.sendMessage(Component.text("║  Списано: " + PropertyManager.LAND_TAX_AMOUNT + " булыж.")
                    .color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("║  (2 стака)")
                    .color(NamedTextColor.GRAY));
            player.sendMessage(Component.text("╚══════════════════════════════╝")
                    .color(NamedTextColor.GREEN));
            player.sendMessage(Component.empty());
        } else {
            if (!pm.hasAnyPlot(uuid)) {
                player.sendMessage(Component.text("[Налог] У вас нет земли!")
                        .color(NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("[Налог] Не хватает булыжника! Нужно: "
                                + PropertyManager.LAND_TAX_AMOUNT)
                        .color(NamedTextColor.RED));
            }
        }
    }

    // === GUI МЕНЮ ===

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

        // Баланс — ТОЛЬКО булыжник в инвентаре
        int cobbleInInventory = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COBBLESTONE) {
                cobbleInInventory += item.getAmount();
            }
        }

        // Заголовок
        ItemStack info = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("ИМУЩЕСТВО").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        im.lore(List.of(
                Component.empty(),
                Component.text("Булыжник в инвентаре: " + cobbleInInventory)
                        .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(im);
        gui.setItem(4, info);

        // Квартира вторичка
        ItemStack apt1 = new ItemStack(Material.BARRIER);
        ItemMeta apt1m = apt1.getItemMeta();
        apt1m.displayName(Component.text("Квартира (вторичка)").color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        apt1m.lore(List.of(Component.text("В разработке...").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        apt1.setItemMeta(apt1m);
        gui.setItem(10, apt1);

        // Квартира новостройка
        ItemStack apt2 = new ItemStack(Material.BARRIER);
        ItemMeta apt2m = apt2.getItemMeta();
        apt2m.displayName(Component.text("Квартира (новостройка)").color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        apt2m.lore(List.of(Component.text("В разработке...").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        apt2.setItemMeta(apt2m);
        gui.setItem(12, apt2);

        // Участок №1
        String plotId = "plot-1";
        boolean isSold = plugin.getPropertyManager().isPlotSold(plotId);
        boolean isMine = plugin.getPropertyManager().hasPlot(uuid, plotId);
        PropertyManager propMgr = plugin.getPropertyManager();
        String coords = propMgr.getPlotCoordinates(plotId);

        ItemStack plot;
        ItemMeta plotMeta;

        if (isMine) {
            plot = new ItemStack(Material.LIME_CONCRETE);
            plotMeta = plot.getItemMeta();
            plotMeta.displayName(Component.text("Участок №1 (ВАШ)").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Этот участок принадлежит вам")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Координаты:").color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(coords).color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Земельный налог: 2 стака / 3 дня")
                    .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/property tax — оплатить")
                    .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));

            if (propMgr.hasAnyInstallment(uuid)) {
                PropertyManager.InstallmentData inst = propMgr.getInstallment(uuid);
                lore.add(Component.empty());
                lore.add(Component.text("Рассрочка: " + inst.remaining + " булыж. осталось")
                        .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("/property pay <сумма>")
                        .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            }

            plotMeta.lore(lore);

        } else if (isSold) {
            plot = new ItemStack(Material.RED_CONCRETE);
            plotMeta = plot.getItemMeta();
            plotMeta.displayName(Component.text("Участок №1 (ПРОДАН)").color(NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));

            UUID ownerUuid = propMgr.getPlotOwner(plotId);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
            String ownerName = owner.getName() != null ? owner.getName() : "Неизвестно";

            plotMeta.lore(List.of(
                    Component.empty(),
                    Component.text("Владелец: " + ownerName).color(NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Координаты: " + coords).color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Участок приобретён другим игроком")
                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ));
        } else {
            plot = new ItemStack(Material.GRASS_BLOCK);
            plotMeta = plot.getItemMeta();

            int price = plugin.getConfigManager().getPlot1PricePerBlock();
            int blocks = plugin.getConfigManager().getPlot1SurfaceBlocks();
            int totalPrice = price * blocks;

            // Считаем всё (инвентарь + сундуки) для покупки
            int totalCobble = ChestScanner.countTotalMaterial(player, Material.COBBLESTONE);

            plotMeta.displayName(Component.text("Участок №1").color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Координаты:").color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(coords).color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Размер: " + blocks + " блок (3 вниз + 20 вверх)")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Цена: " + totalPrice + " булыжников")
                    .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Земельный налог: 2 стака / 3 игр. дня")
                    .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
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

            plotMeta.lore(lore);
        }

        plot.setItemMeta(plotMeta);
        gui.setItem(14, plot);

        // Моя собственность
        ItemStack myProp = new ItemStack(Material.BOOK);
        ItemMeta myPm = myProp.getItemMeta();
        List<PropertyManager.OwnedPlot> owned = propMgr.getOwnedPlots(uuid);
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

    // === ОБРАБОТКА КЛИКОВ ===

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof MineInventoryHolder holder)) return;

        if (holder.getType() != MineInventoryHolder.GUIType.PROPERTY_MENU) return;

        event.setCancelled(true);

        if (event.getRawSlot() == 14) {
            UUID uuid = player.getUniqueId();
            String plotId = "plot-1";
            PropertyManager pm = plugin.getPropertyManager();

            if (pm.isPlotSold(plotId)) {
                if (pm.hasPlot(uuid, plotId)) {
                    player.sendMessage(Component.text("[Имущество] Этот участок уже ваш!")
                            .color(NamedTextColor.YELLOW));
                } else {
                    UUID ownerUuid = pm.getPlotOwner(plotId);
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
                    String ownerName = owner.getName() != null ? owner.getName() : "Неизвестно";
                    player.sendMessage(Component.text("[Имущество] Участок продан: " + ownerName)
                            .color(NamedTextColor.RED));
                }
                return;
            }

            int price = plugin.getConfigManager().getPlot1PricePerBlock();
            int blocks = plugin.getConfigManager().getPlot1SurfaceBlocks();
            int totalPrice = price * blocks;

            if (event.isLeftClick()) {
                boolean success = pm.buyPlotFull(uuid, plotId, totalPrice, player);

                if (success) {
                    player.closeInventory();
                    String coords = pm.getPlotCoordinates(plotId);
                    TaxUtils.playSuccessSound(player);

                    // Выдать достижение за покупку участка
                    plugin.getAchievementManager().unlockLandlord(uuid);

                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("╔══════════════════════════════════╗")
                            .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.text("║  УЧАСТОК КУПЛЕН!")
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.BOLD, true));
                    player.sendMessage(Component.text("║  Участок №1")
                            .color(NamedTextColor.WHITE));
                    player.sendMessage(Component.text("║  Координаты: " + coords)
                            .color(NamedTextColor.GRAY));
                    player.sendMessage(Component.text("║  Списано: " + totalPrice + " булыж.")
                            .color(NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("║  Земельный налог: 2 стака / 3 дня")
                            .color(NamedTextColor.RED));
                    player.sendMessage(Component.text("║  /property tax — оплатить")
                            .color(NamedTextColor.AQUA));
                    player.sendMessage(Component.text("╚══════════════════════════════════╝")
                            .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.empty());
                    plugin.getPassportManager().updateResidence(uuid, "Участок №1");
                } else {
                    player.sendMessage(Component.text("[Имущество] Не хватает булыжника!")
                            .color(NamedTextColor.RED));
                }

            } else if (event.isRightClick()) {
                if (pm.hasAnyInstallment(uuid)) {
                    player.sendMessage(Component.text("[Имущество] У вас уже есть рассрочка!")
                            .color(NamedTextColor.RED));
                    return;
                }

                var result = pm.evaluateInstallment(uuid, plotId, totalPrice, player);

                if (result.approved) {
                    pm.createInstallment(uuid, plotId, totalPrice, result.termDays);
                    player.closeInventory();
                    String coords = pm.getPlotCoordinates(plotId);
                    plugin.getPassportManager().updateResidence(uuid, "Участок №1 (рассрочка)");

                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("╔══════════════════════════════════╗")
                            .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.text("║  РАССРОЧКА ОДОБРЕНА!")
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.BOLD, true));
                    player.sendMessage(Component.text("║  Участок №1")
                            .color(NamedTextColor.WHITE));
                    player.sendMessage(Component.text("║  Координаты: " + coords)
                            .color(NamedTextColor.GRAY));
                    player.sendMessage(Component.text("║  Стоимость: " + totalPrice)
                            .color(NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("║  Срок: " + result.termDays + " игр. дней")
                            .color(NamedTextColor.AQUA));
                    player.sendMessage(Component.text("║  Просрочка: +3%/игр. день")
                            .color(NamedTextColor.RED));
                    player.sendMessage(Component.text("║  " + result.message)
                            .color(NamedTextColor.DARK_GRAY));
                    player.sendMessage(Component.text("║")
                            .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.text("║  /property pay <сумма> — оплатить")
                            .color(NamedTextColor.WHITE));
                    player.sendMessage(Component.text("║  /property info — подробности")
                            .color(NamedTextColor.WHITE));
                    player.sendMessage(Component.text("╚══════════════════════════════════╝")
                            .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.empty());
                } else {
                    player.sendMessage(Component.text("[Имущество] Рассрочка отклонена: "
                                    + result.message)
                            .color(NamedTextColor.RED));
                }
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                  @NotNull Command command,
                                                  @NotNull String alias,
                                                  @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("pay");
            completions.add("info");
            completions.add("tax");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pay")) {
            completions.add("64");
            completions.add("128");
            completions.add("all");
            completions.add("1s");
            completions.add("2s");
        }
        return completions;
    }
}
