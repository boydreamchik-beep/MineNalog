package com.mine.plugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Barrel;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Сканирует все сундуки, бочки и шалкеры игрока.
 * Также считает булыжник в инвентаре.
 */
public class ChestScanner {

    // Радиус поиска сундуков вокруг игрока (в блоках)
    private static final int SCAN_RADIUS = 100;

    /**
     * Подсчитать ВСЁ количество материала у игрока:
     * инвентарь + все контейнеры в радиусе
     */
    public static int countTotalMaterial(Player player, Material material) {
        int total = 0;

        // Инвентарь
        total += countInInventory(player, material);

        // Контейнеры рядом
        total += countInNearbyContainers(player, material);

        return total;
    }

    /**
     * Подсчитать материал в инвентаре
     */
    public static int countInInventory(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Подсчитать материал во всех контейнерах рядом
     */
    public static int countInNearbyContainers(Player player, Material material) {
        int count = 0;
        int px = player.getLocation().getBlockX();
        int py = player.getLocation().getBlockY();
        int pz = player.getLocation().getBlockZ();

        for (int x = px - SCAN_RADIUS; x <= px + SCAN_RADIUS; x++) {
            for (int y = Math.max(py - 20, -64); y <= Math.min(py + 20, 320); y++) {
                for (int z = pz - SCAN_RADIUS; z <= pz + SCAN_RADIUS; z++) {
                    Block block = player.getWorld().getBlockAt(x, y, z);
                    Inventory containerInv = getContainerInventory(block);
                    if (containerInv != null) {
                        for (ItemStack item : containerInv.getContents()) {
                            if (item != null && item.getType() == material) {
                                count += item.getAmount();
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Удалить материал из инвентаря и контейнеров
     */
    public static int removeMaterialFromAll(Player player, Material material, int amount) {
        int remaining = amount;

        // Сначала из инвентаря
        remaining = removeFromInventory(player, material, remaining);

        if (remaining <= 0) return 0;

        // Потом из контейнеров
        remaining = removeFromNearbyContainers(player, material, remaining);

        return remaining; // Возвращает сколько не удалось удалить
    }

    private static int removeFromInventory(Player player, Material material, int amount) {
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
        return remaining;
    }

    private static int removeFromNearbyContainers(Player player, Material material, int amount) {
        int remaining = amount;
        int px = player.getLocation().getBlockX();
        int py = player.getLocation().getBlockY();
        int pz = player.getLocation().getBlockZ();

        for (int x = px - SCAN_RADIUS; x <= px + SCAN_RADIUS && remaining > 0; x++) {
            for (int y = Math.max(py - 20, -64); y <= Math.min(py + 20, 320) && remaining > 0; y++) {
                for (int z = pz - SCAN_RADIUS; z <= pz + SCAN_RADIUS && remaining > 0; z++) {
                    Block block = player.getWorld().getBlockAt(x, y, z);
                    Inventory inv = getContainerInventory(block);
                    if (inv == null) continue;

                    for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
                        ItemStack item = inv.getItem(i);
                        if (item == null || item.getType() != material) continue;

                        if (item.getAmount() <= remaining) {
                            remaining -= item.getAmount();
                            inv.setItem(i, null);
                        } else {
                            item.setAmount(item.getAmount() - remaining);
                            remaining = 0;
                        }
                    }
                }
            }
        }
        return remaining;
    }

    private static Inventory getContainerInventory(Block block) {
        BlockState state = block.getState();
        if (state instanceof Chest chest) return chest.getInventory();
        if (state instanceof Barrel barrel) return barrel.getInventory();
        if (state instanceof ShulkerBox shulker) return shulker.getInventory();
        return null;
    }
}
