package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

/**
 * Генератор блоков в шахте.
 * На координатах (-230, 43, -67) постоянно генерируется
 * случайный блок: камень, диорит, андезит или гранит.
 * 
 * Когда игрок ломает блок — через 2 секунды появляется новый.
 */
public class MineGenerator implements Listener {

    private final MinePlugin plugin;
    private final Random random = new Random();

    // Координаты генератора
    private static final int GEN_X = -231;
    private static final int GEN_Y = 43;
    private static final int GEN_Z = -68;

    // Задержка перед генерацией нового блока (в тиках, 20 тиков = 1 секунда)
    private static final long REGEN_DELAY_TICKS = 40L; // 2 секунды

    // Блоки которые генерируются
    private static final Material[] GENERATOR_BLOCKS = {
            Material.STONE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.GRANITE
    };

    // Имя мира (null = определяется автоматически)
    private String worldName = null;

    public MineGenerator(MinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Запуск генератора — ставит первый блок при старте сервера
     */
    public void start() {
        // Ставим блок через 3 секунды после запуска (чтобы мир загрузился)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            generateBlock();
            plugin.getLogger().info("Генератор шахты запущен на координатах: "
                    + GEN_X + ", " + GEN_Y + ", " + GEN_Z);
        }, 60L);
    }

    /**
     * Генерирует случайный блок на координатах генератора
     */
    private void generateBlock() {
        Block block = getGeneratorBlock();
        if (block == null) return;

        Material randomBlock = GENERATOR_BLOCKS[random.nextInt(GENERATOR_BLOCKS.length)];
        block.setType(randomBlock);
    }

    /**
     * Получить блок генератора
     */
    private Block getGeneratorBlock() {
        World world;

        if (worldName != null) {
            world = Bukkit.getWorld(worldName);
        } else {
            // Берём первый загруженный мир
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }

        if (world == null) {
            plugin.getLogger().warning("Мир не найден для генератора!");
            return null;
        }

        return world.getBlockAt(GEN_X, GEN_Y, GEN_Z);
    }

    /**
     * Проверяет, является ли блок генератором
     */
    private boolean isGeneratorBlock(Block block) {
        return block.getX() == GEN_X
                && block.getY() == GEN_Y
                && block.getZ() == GEN_Z;
    }

    /**
     * Когда игрок ломает блок генератора —
     * через REGEN_DELAY_TICKS появляется новый
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();

        if (!isGeneratorBlock(block)) return;

        // Запланировать генерацию нового блока
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            generateBlock();
        }, REGEN_DELAY_TICKS);
    }

    /**
     * Установить имя мира (если нужен конкретный мир)
     */
    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }
}
