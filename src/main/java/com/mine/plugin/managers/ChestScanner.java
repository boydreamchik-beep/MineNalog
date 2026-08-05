package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Сканирует сундуки, бочки и шалкеры в загруженных чанках вокруг игрока.
 * Только tile entities — не перебирает 1.6 млн блоков!
 */
public class ChestScanner {

    /** Радиус поиска чанков (в чанках) */
    private static int getScanChunkRadius() {
        try {
            return MinePlugin.getInstance().getConfigManager().getScanRadiusBlocks(); // пока дефолт
        } catch (Exception e) {
            return 7; // ~112 блоков
        }
    }

    private static boolean isContainer(BlockState state) {
        return state instanceof Chest || state instanceof Barrel || state instanceof ShulkerBox;
    }

    private static Location inventoryKey(TileState state, Inventory inv) {
        // Двойной сундук имеет один общий location
        if (inv.getHolder() instanceof org.bukkit.block.DoubleChest dc) {
            return dc.getLocation();
        }
        return state.getBlock().getLocation();
    }

    /**
     * Перебрать все контейнеры рядом и применить action.
     * action получает (BlockState, Inventory).
     */
    public static void forEachContainer(Player player, BiConsumer<TileState, Inventory> action) {
        World world = player.getWorld();
        Location loc = player.getLocation();

        double radiusSq = (double) getScanChunkRadius() * 16;
        radiusSq *= radiusSq; // чанки радиуса в блоках в квадрате

        int pcx = loc.getBlockX() >> 4;
        int pcz = loc.getBlockZ() >> 4;
        int chunkRad = getScanChunkRadius();

        Set<Location> visited = new HashSet<>();

        for (int cx = pcx - chunkRad; cx <= pcx + chunkRad; cx++) {
            for (int cz = pcz - chunkRad; cz <= pcz + chunkRad; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (TileState state : world.getChunkAt(cx, cz).getTileEntities()) {
                    if (!isContainer(state)) continue;
                    Block block = state.getBlock();
                    if (block.getLocation().distanceSquared(loc) > radiusSq) continue;

                    // Получаем инвентарь через Container (безопасный каст)
                    if (!(state instanceof org.bukkit.block.Container container)) continue;
                    Inventory inv = container.getInventory();
                    if (inv == null) continue;

                    if (!visited.add(inventoryKey(state, inv))) continue; // двойник уже был
                    action.accept(state, inv);
                }
            }
        }
    }

    /**
     * Всего материала у игрока: инвентарь + контейнеры.
     */
    public static int countTotalMaterial(Player player, Material material) {
        int[] total = {TaxUtils.countInInventory(player, material)};
        forEachContainer(player, (state, inv) -> {
            for (var item : inv.getContents()) {
                if (item != null && item.getType() == material) total[0] += item.getAmount();
            }
        });
        return total[0];
    }

    /**
     * Только в инвентаре (быстро для GUI).
     */
    public static int countInNearbyContainers(Player player, Material material) {
        int[] count = {0};
        forEachContainer(player, (state, inv) -> {
            for (var item : inv.getContents()) {
                if (item != null && item.getType() == material) count[0] += item.getAmount();
            }
        });
        return count[0];
    }

    /**
     * Списать материал из инвентаря и контейнеров.
     * @return сколько осталось списать (0 если всё ок)
     */
    public static int removeMaterialFromAll(Player player, Material material, int amount) {
        int[] remaining = {amount};

        // Сначала инвентарь
        remaining[0] = TaxUtils.removeFromInventory(player, material, remaining[0]);
        if (remaining[0] <= 0) return 0;

        // Потом контейнеры (упорядоченно, можно остановиться когда хватит)
        forEachContainer(player, (state, inv) -> {
            if (remaining[0] <= 0) return;
            for (int i = 0; i < inv.getSize() && remaining[0] > 0; i++) {
                var item = inv.getItem(i);
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
}
