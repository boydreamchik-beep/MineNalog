package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Сканирует контейнеры игрока (сундуки, бочки, шалкеры) в радиусе.
 * Оптимизированная версия: итерация только по TileEntity загруженных чанков.
 * НЕ загружает чанки насильно — работает только с уже загруженными.
 */
public class ChestScanner {

    // ThreadLocal для потокобезопасности — каждый вызов имеет свой Set
    private static final ThreadLocal<Set<Location>> VISITED_DOUBLE_CHESTS =
            ThreadLocal.withInitial(HashSet::new);

    private ChestScanner() {}

    /**
     * Подсчитать ВСЁ количество материала у игрока:
     * инвентарь + все контейнеры в радиусе
     */
    public static int countTotalMaterial(Player player, Material material) {
        int total = countInInventory(player, material);
        total += countInNearbyContainers(player, material);
        return total;
    }

    /**
     * Подсчитать материал в инвентаре игрока
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
     * Подсчитать материал во всех контейнерах рядом (только загруженные чанки)
     */
    public static int countInNearbyContainers(Player player, Material material) {
        int[] count = {0};
        forEachContainer(player, (state, inv) -> {
            for (ItemStack item : inv.getContents()) {
                if (item != null && item.getType() == material) {
                    count[0] += item.getAmount();
                }
            }
        });
        return count[0];
    }

    /**
     * Удалить материал из инвентаря и контейнеров
     * @return количество, которое НЕ удалось удалить (0 если всё успешно)
     */
    public static int removeMaterialFromAll(Player player, Material material, int amount) {
        int remaining = removeFromInventory(player, material, amount);
        if (remaining <= 0) return 0;
        return removeFromNearbyContainers(player, material, remaining);
    }

    /**
     * Удалить материал из инвентаря игрока
     * @return остаток который не удалось удалить
     */
    private static int removeFromInventory(Player player, Material material, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != material) continue;
            
            // Защита компаса шахты
            if (isMineCompass(item)) continue;

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

    /**
     * Удалить материал из контейнеров рядом
     * @return остаток который не удалось удалить
     */
    private static int removeFromNearbyContainers(Player player, Material material, int amount) {
        int[] remaining = {amount};
        forEachContainer(player, (state, inv) -> {
            if (remaining[0] <= 0) return;
            for (int i = 0; i < inv.getSize() && remaining[0] > 0; i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() != material) continue;

                if (item.getAmount() <= remaining[0]) {
                    remaining[0] -= item.getAmount();
                    inv.setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining[0]);
                    remaining[0] = 0;
                }
            }
        });
        return remaining[0];
    }

    /**
     * Перебрать все контейнеры в радиусе (только загруженные чанки)
     */
    private static void forEachContainer(Player player, BiConsumer<BlockState, Inventory> action) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        int scanRadius = MinePlugin.getInstance().getConfigManager().getScanRadius();
        double radiusSq = (double) scanRadius * scanRadius;
        
        int pcx = loc.getBlockX() >> 4;
        int pcz = loc.getBlockZ() >> 4;
        int chunkRadius = (scanRadius + 15) >> 4;
        
        Set<Location> visited = VISITED_DOUBLE_CHESTS.get();
        visited.clear();

        try {
            for (int cx = pcx - chunkRadius; cx <= pcx + chunkRadius; cx++) {
                for (int cz = pcz - chunkRadius; cz <= pcz + chunkRadius; cz++) {
                    // НЕ загружаем чанки насильно!
                    if (!world.isChunkLoaded(cx, cz)) continue;

                    for (BlockState state : world.getChunkAt(cx, cz).getTileEntities()) {
                        if (!(state instanceof Container container)) continue;

                        Block block = state.getBlock();
                        if (block.getLocation().distanceSquared(loc) > radiusSq) continue;

                        Inventory inv = container.getInventory();

                        // Дедупликация двойных сундуков
                        if (inv.getHolder() instanceof DoubleChest dc) {
                            Location key = dc.getLocation();
                            if (!visited.add(key)) continue;
                        }

                        action.accept(state, inv);
                    }
                }
            }
        } finally {
            visited.clear();
        }
    }

    /**
     * Проверка является ли предмет компасом шахты
     */
    private static boolean isMineCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        if (!item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return false;
        String name = net.kyori.adventure.text.serializer.plain
                .PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        return name.contains("Выход из шахты");
    }
}
