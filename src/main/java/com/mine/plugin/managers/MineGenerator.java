package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Random;

/**
 * ИЗМЕНЕНИЯ:
 * - Y координата: 42 (на 1 ниже, было 43)
 * - Моментальная генерация (задержка = 1 тик, минимально возможная)
 */
public class MineGenerator implements Listener {

    private final MinePlugin plugin;
    private final Random random = new Random();

    // Координаты генератора (Y на 1 ниже = 42)
    private static final int GEN_X = -231;
    private static final int GEN_Y = 42;      // ← ИЗМЕНЕНО: было 43
    private static final int GEN_Z = -68;

    // Моментальная генерация (1 тик = 0.05 сек)
    private static final long REGEN_DELAY_TICKS = 1L;  // ← ИЗМЕНЕНО: было 40

    // Блоки которые генерируются
    private static final Material[] GENERATOR_BLOCKS = {
            Material.STONE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.GRANITE
    };

    private String worldName = null;

    public MineGenerator(MinePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            generateBlock();
            plugin.getLogger().info("Генератор шахты запущен: "
                    + GEN_X + ", " + GEN_Y + ", " + GEN_Z);
        }, 60L);
    }

    private void generateBlock() {
        Block block = getGeneratorBlock();
        if (block == null) return;

        Material randomBlock = GENERATOR_BLOCKS[random.nextInt(GENERATOR_BLOCKS.length)];
        block.setType(randomBlock);
    }

    private Block getGeneratorBlock() {
        World world;
        if (worldName != null) {
            world = Bukkit.getWorld(worldName);
        } else {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            plugin.getLogger().warning("Мир не найден для генератора!");
            return null;
        }
        return world.getBlockAt(GEN_X, GEN_Y, GEN_Z);
    }

    private boolean isGeneratorBlock(Block block) {
        return block.getX() == GEN_X
                && block.getY() == GEN_Y
                && block.getZ() == GEN_Z;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        if (!isGeneratorBlock(block)) return;

        // Моментальная генерация
        Bukkit.getScheduler().runTaskLater(plugin, this::generateBlock, REGEN_DELAY_TICKS);
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }
}
