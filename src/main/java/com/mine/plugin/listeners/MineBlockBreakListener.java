package com.mine.plugin.listeners;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.gui.MineLevelGUI;
import com.mine.plugin.managers.ConfigManager;
import com.mine.plugin.managers.ConfigManager.LevelConfig;
import com.mine.plugin.managers.TaxTracker;
import com.mine.plugin.utils.TaxUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class MineBlockBreakListener implements Listener {

    private final MinePlugin plugin;
    private final MineLevelGUI mineLevelGUI;
    private final TaxTracker taxTracker;

    public MineBlockBreakListener(MinePlugin plugin, MineLevelGUI mineLevelGUI,
                                   TaxTracker taxTracker) {
        this.plugin = plugin;
        this.mineLevelGUI = mineLevelGUI;
        this.taxTracker = taxTracker;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!mineLevelGUI.isPlayerInMine(uuid)) return;

        LevelConfig level = mineLevelGUI.getPlayerLevel(uuid);
        if (level == null) return;

        ConfigManager cfg = plugin.getConfigManager();

        double bx = event.getBlock().getX();
        double by = event.getBlock().getY();
        double bz = event.getBlock().getZ();

        if (bx < level.zoneMinX || bx > level.zoneMaxX
                || by < level.zoneMinY || by > level.zoneMaxY
                || bz < level.zoneMinZ || bz > level.zoneMaxZ) {
            return;
        }

        Material brokenBlock = event.getBlock().getType();
        event.setDropItems(false);

        // Ценные руды -> казна
        if (cfg.isTreasuryOre(brokenBlock)) {
            plugin.getKaznaManager().addItem(brokenBlock, 1);
            plugin.getIncomeTracker().recordTaxPaid(uuid, 1);

            player.sendMessage(Component.text("[Шахта] ")
                    .color(NamedTextColor.DARK_GREEN)
                    .append(Component.text(TaxUtils.getRussianName(brokenBlock))
                            .color(NamedTextColor.GOLD))
                    .append(Component.text(" отправлена в казну!")
                            .color(NamedTextColor.YELLOW)));
            return;
        }

        // Определяем материалы
        boolean isTaxAsOriginal = cfg.isTaxAsOriginal(brokenBlock);
        boolean isTaxBlock = taxTracker.incrementAndCheckTax(uuid, level.getTaxEvery());

        Material taxMaterial = isTaxAsOriginal ? brokenBlock : Material.COBBLESTONE;
        Material playerMaterial = Material.COBBLESTONE;

        if (brokenBlock == Material.COBBLESTONE) {
            taxMaterial = Material.COBBLESTONE;
            playerMaterial = Material.COBBLESTONE;
        }

        if (isTaxBlock) {
            plugin.getKaznaManager().addItem(taxMaterial, 1);
            plugin.getIncomeTracker().recordTaxPaid(uuid, 1);

            player.sendMessage(Component.text("[Налог] ")
                    .color(NamedTextColor.RED)
                    .append(Component.text(TaxUtils.getRussianName(taxMaterial)
                                    + " удержан как налог " + level.taxPercent + "%")
                            .color(NamedTextColor.GRAY)));
        } else {
            giveItem(player, playerMaterial, 1);
            plugin.getIncomeTracker().recordMined(uuid, 1);

            // Проверка достижений за добычу
            plugin.getAchievementManager().checkMining(uuid);

            if (brokenBlock != Material.COBBLESTONE && brokenBlock != playerMaterial) {
                player.sendActionBar(Component.text(
                                TaxUtils.getRussianName(brokenBlock) + " → Булыжник")
                        .color(NamedTextColor.YELLOW));
            }
        }
    }

    private void giveItem(Player player, Material material, int amount) {
        Map<Integer, ItemStack> overflow = player.getInventory()
                .addItem(new ItemStack(material, amount));
        for (ItemStack item : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }
}
