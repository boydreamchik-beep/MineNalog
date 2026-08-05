package com.mine.plugin.gui;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.listeners.CompassListener;
import com.mine.plugin.managers.ConfigManager;
import com.mine.plugin.managers.ConfigManager.LevelConfig;
import com.mine.plugin.utils.TaxUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MineLevelGUI implements Listener {

    private final MinePlugin plugin;
    private final Set<UUID> playersInMine = new HashSet<>();
    private final Set<UUID> playersInForcedGUI = new HashSet<>();

    // Какой уровень выбрал игрок
    private final Map<UUID, LevelConfig> playerLevels = new HashMap<>();

    public MineLevelGUI(MinePlugin plugin) {
        this.plugin = plugin;
    }

    public void openMenu(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getFreezeManager().freeze(player);
        playersInForcedGUI.add(uuid);

        ConfigManager cfg = plugin.getConfigManager();
        List<LevelConfig> levels = cfg.getLevels();

        int guiSize = 27;
        MineInventoryHolder holder = new MineInventoryHolder(
                MineInventoryHolder.GUIType.MINE_LEVEL_SELECT);

        Inventory gui = Bukkit.createInventory(holder, guiSize,
                Component.text("Выбор уровня шахты")
                        .color(NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.BOLD, true));

        ItemStack glass = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < guiSize; i++) gui.setItem(i, glass);

        // Заголовок
        ItemStack info = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("ГОСУДАРСТВЕННАЯ ШАХТА")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.empty());
        infoLore.add(Component.text("Выберите уровень шахты")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        // Уровни
        int[] levelSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < levels.size() && i < levelSlots.length; i++) {
            LevelConfig level = levels.get(i);
            ItemStack item;

            if (level.enabled) {
                item = new ItemStack(Material.IRON_PICKAXE);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(level.name)
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("Высота: Y = " + level.height)
                        .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Налог: " + level.taxPercent + "% (каждый "
                                + level.getTaxEvery() + "-й блок)")
                        .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("Нажмите для телепортации!")
                        .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, true));
                meta.lore(lore);
                item.setItemMeta(meta);
            } else {
                item = new ItemStack(Material.BARRIER);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(level.name)
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("В разработке...")
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
                meta.lore(lore);
                item.setItemMeta(meta);
            }

            gui.setItem(levelSlots[i], item);
        }

        // Кнопка "Уйти"
        ItemStack exitBtn = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta exitMeta = exitBtn.getItemMeta();
        exitMeta.displayName(Component.text("Уйти")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        exitBtn.setItemMeta(exitMeta);
        gui.setItem(22, exitBtn);

        player.openInventory(gui);
    }

    public void openAlreadyInMineMenu(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getFreezeManager().freeze(player);
        playersInForcedGUI.add(uuid);

        MineInventoryHolder holder = new MineInventoryHolder(
                MineInventoryHolder.GUIType.ALREADY_IN_MINE);

        Inventory gui = Bukkit.createInventory(holder, 27,
                Component.text("Вы уже в шахте!")
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true));

        ItemStack glass = createGlass(Material.RED_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) gui.setItem(i, glass);

        ItemStack info = new ItemStack(Material.BARRIER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Нельзя выбрать уровень!")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        ItemStack exitBtn = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta exitMeta = exitBtn.getItemMeta();
        exitMeta.displayName(Component.text("Выйти из шахты")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        exitBtn.setItemMeta(exitMeta);
        gui.setItem(13, exitBtn);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof MineInventoryHolder holder)) return;

        UUID uuid = player.getUniqueId();

        if (holder.getType() == MineInventoryHolder.GUIType.MINE_LEVEL_SELECT) {
            event.setCancelled(true);
            int slot = event.getRawSlot();

            // Уровни на слотах 10-16
            int[] levelSlots = {10, 11, 12, 13, 14, 15, 16};
            List<LevelConfig> levels = plugin.getConfigManager().getLevels();

            for (int i = 0; i < levelSlots.length && i < levels.size(); i++) {
                if (slot == levelSlots[i]) {
                    LevelConfig level = levels.get(i);

                    if (!level.enabled) {
                        player.sendMessage(Component.text(level.name + " в разработке!")
                                .color(NamedTextColor.RED));
                        return;
                    }

                    // Выбран уровень
                    playersInForcedGUI.remove(uuid);
                    plugin.getFreezeManager().unfreeze(player);
                    player.closeInventory();

                    Location loc = new Location(player.getWorld(),
                            level.teleportX, level.teleportY, level.teleportZ);
                    loc.setYaw(player.getLocation().getYaw());
                    loc.setPitch(player.getLocation().getPitch());
                    player.teleport(loc);

                    playersInMine.add(uuid);
                    playerLevels.put(uuid, level);
                    plugin.getTaxTracker().reset(uuid);
                    CompassListener.giveCompass(player);
                    plugin.getIncomeTracker().recordMineVisit(uuid);

                    // Проверка достижений за посещение
                    plugin.getAchievementManager().checkMineVisit(uuid);

                    TaxUtils.playTeleportSound(player);
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("==============================")
                            .color(NamedTextColor.DARK_GREEN));
                    player.sendMessage(Component.text(" " + level.name + "!")
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.BOLD, true));
                    player.sendMessage(Component.text(" Высота: Y = " + level.height)
                            .color(NamedTextColor.GRAY));
                    player.sendMessage(Component.text(" Налог: " + level.taxPercent + "%")
                            .color(NamedTextColor.RED));
                    player.sendMessage(Component.text(" Компас (слот 9) — выход")
                            .color(NamedTextColor.AQUA));
                    player.sendMessage(Component.text("==============================")
                            .color(NamedTextColor.DARK_GREEN));
                    return;
                }
            }

            if (slot == 22) {
                playersInForcedGUI.remove(uuid);
                plugin.getFreezeManager().unfreeze(player);
                player.closeInventory();
            }
            return;
        }

        if (holder.getType() == MineInventoryHolder.GUIType.ALREADY_IN_MINE) {
            event.setCancelled(true);
            if (event.getRawSlot() == 13) {
                playersInForcedGUI.remove(uuid);
                plugin.getFreezeManager().unfreeze(player);
                player.closeInventory();
                performMineExit(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (!playersInForcedGUI.contains(uuid)) return;
        if (!(event.getInventory().getHolder() instanceof MineInventoryHolder holder)) return;

        MineInventoryHolder.GUIType type = holder.getType();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (!playersInForcedGUI.contains(uuid)) return;

            if (type == MineInventoryHolder.GUIType.MINE_LEVEL_SELECT) {
                openMenu(player);
            } else if (type == MineInventoryHolder.GUIType.ALREADY_IN_MINE) {
                openAlreadyInMineMenu(player);
            }
        }, 1L);
    }

    private void performMineExit(Player player) {
        UUID uuid = player.getUniqueId();
        playersInMine.remove(uuid);
        playerLevels.remove(uuid);
        plugin.getTaxTracker().reset(uuid);
        plugin.getFreezeManager().unfreeze(uuid);
        CompassListener.removeCompass(player);

        ConfigManager cfg = plugin.getConfigManager();
        Location exitLoc = new Location(player.getWorld(),
                cfg.getEntryX(), cfg.getEntryY(), cfg.getEntryZ());
        exitLoc.setYaw(player.getLocation().getYaw());
        exitLoc.setPitch(player.getLocation().getPitch());
        player.teleport(exitLoc);

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("==============================")
                .color(NamedTextColor.GREEN));
        player.sendMessage(Component.text(" Вы вышли из шахты!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("==============================")
                .color(NamedTextColor.GREEN));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playersInMine.remove(uuid);
        playerLevels.remove(uuid);
        playersInForcedGUI.remove(uuid);
        plugin.getTaxTracker().reset(uuid);
        plugin.getFreezeManager().unfreeze(uuid);
        CompassListener.removeCompass(event.getPlayer());
    }

    public boolean isPlayerInMine(UUID uuid) { return playersInMine.contains(uuid); }
    public void removePlayerFromMine(UUID uuid) {
        playersInMine.remove(uuid);
        playerLevels.remove(uuid);
        playersInForcedGUI.remove(uuid);
    }
    public Set<UUID> getPlayersInMine() { return playersInMine; }
    public LevelConfig getPlayerLevel(UUID uuid) { return playerLevels.get(uuid); }

    private ItemStack createGlass(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);
        return glass;
    }
}
