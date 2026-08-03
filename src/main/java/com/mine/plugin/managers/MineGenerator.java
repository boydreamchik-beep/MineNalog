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
 * - Генератор 1 сдвинут на 1 блок левее (X = -232)
 * - Генератор 2 остался на X = -230
 * - Между ними 1 блок расстояния (X = -231 — пустой)
 * 
 * Схема (вид сверху, Z = -68, Y = 42):
 * 
 *   X = -232     X = -231     X = -230
 *   ┌──────┐    ┌──────┐    ┌──────┐
 *   │  G1  │    │ ПУСТО│    │  G2  │
 *   │ Лев. │    │      │    │ Прав.│
 *   └──────┘    └──────┘    └──────┘
 */
public class MineGenerator implements Listener {

    private final MinePlugin plugin;
    private final Random random = new Random();

    // Генератор 1 (левый) — сдвинут на 1 блок левее
    private static final int GEN1_X = -232;    // ← ИЗМЕНЕНО: было -231
    private static final int GEN1_Y = 42;
    private static final int GEN1_Z = -68;

    // Генератор 2 (правый)
    private static final int GEN2_X = -230;
    private static final int GEN2_Y = 42;
    private static final int GEN2_Z = -68;

    // Между ними X = -231 — пустой блок (расстояние 1)

    // Моментальная генерация
    private static final long REGEN_DELAY_TICKS = 1L;

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

    /**
     * Запуск обоих генераторов
     */
    public void start() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            generateBlock(GEN1_X, GEN1_Y, GEN1_Z);
            generateBlock(GEN2_X, GEN2_Y, GEN2_Z);
            plugin.getLogger().info("Генератор 1 (лев.): "
                    + GEN1_X + ", " + GEN1_Y + ", " + GEN1_Z);
            plugin.getLogger().info("Генератор 2 (прав.): "
                    + GEN2_X + ", " + GEN2_Y + ", " + GEN2_Z);
            plugin.getLogger().info("Расстояние между генераторами: 1 блок");
        }, 60L);
    }

    /**
     * Генерирует случайный блок на указанных координатах
     */
    private void generateBlock(int x, int y, int z) {
        World world = getWorld();
        if (world == null) return;

        Block block = world.getBlockAt(x, y, z);
        Material randomBlock = GENERATOR_BLOCKS[random.nextInt(GENERATOR_BLOCKS.length)];
        block.setType(randomBlock);
    }

    /**
     * Получить мир
     */
    private World getWorld() {
        World world;
        if (worldName != null) {
            world = Bukkit.getWorld(worldName);
        } else {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            plugin.getLogger().warning("Мир не найден для генератора!");
        }
        return world;
    }

    /**
     * Проверяет, является ли блок одним из генераторов.
     * Возвращает номер генератора (1 или 2) или 0 если не генератор.
     */
    private int getGeneratorId(Block block) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        if (x == GEN1_X && y == GEN1_Y && z == GEN1_Z) return 1;
        if (x == GEN2_X && y == GEN2_Y && z == GEN2_Z) return 2;
        return 0;
    }

    /**
     * При разрушении генератора — моментально создаём новый
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        int genId = getGeneratorId(block);

        if (genId == 0) return;

        if (genId == 1) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                generateBlock(GEN1_X, GEN1_Y, GEN1_Z);
            }, REGEN_DELAY_TICKS);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                generateBlock(GEN2_X, GEN2_Y, GEN2_Z);
            }, REGEN_DELAY_TICKS);
        }
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }
}
