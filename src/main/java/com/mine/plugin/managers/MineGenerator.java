package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.managers.ConfigManager.GeneratorConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MineGenerator implements Listener {

    private final MinePlugin plugin;
    private final Random random = new Random();
    private BukkitTask startTask;

    public MineGenerator(MinePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        startTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<GeneratorConfig> generators = plugin.getConfigManager().getGenerators();
            for (GeneratorConfig gen : generators) {
                if (gen.enabled) {
                    generateBlock(gen);
                    plugin.getLogger().info("Генератор " + gen.id + ": "
                            + gen.x + ", " + gen.y + ", " + gen.z);
                }
            }
        }, 60L);
    }

    public void stop() {
        if (startTask != null) {
            startTask.cancel();
            startTask = null;
        }
    }

    private World getWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        return world;
    }

    private void generateBlock(GeneratorConfig gen) {
        World world = getWorld(gen.worldName);
        if (world == null) return;

        List<Material> blocks = plugin.getConfigManager().getGeneratorBlocks();
        if (blocks.isEmpty()) return;

        Block block = world.getBlockAt(gen.x, gen.y, gen.z);
        Material randomBlock = blocks.get(random.nextInt(blocks.size()));
        block.setType(randomBlock);
    }

    private GeneratorConfig getGeneratorAt(Block block) {
        String worldName = block.getWorld().getName();
        List<GeneratorConfig> generators = plugin.getConfigManager().getGenerators();
        for (GeneratorConfig gen : generators) {
            if (gen.enabled
                    && gen.worldName.equals(worldName)
                    && block.getX() == gen.x
                    && block.getY() == gen.y
                    && block.getZ() == gen.z) {
                return gen;
            }
        }
        return null;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        GeneratorConfig gen = getGeneratorAt(event.getBlock());
        if (gen == null) return;

        long delay = plugin.getConfigManager().getRegenDelayTicks();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            generateBlock(gen);
        }, delay);
    }
}
