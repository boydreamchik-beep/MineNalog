package com.mine.plugin.listeners;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.gui.MineLevelGUI;
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

/**
 * ИЗМЕНЕНИЯ:
 * - Диорит/Андезит: налог уходит в казну как ДИОРИТ/АНДЕЗИТ (не булыжник)
 * - Остаток после налога игроку выдаётся как БУЛЫЖНИК
 * - Булыжник: налог как булыжник, остаток как булыжник
 * - Прочие блоки: всё превращается в булыжник, налог булыжником
 * 
 * Схема:
 *   Диорит добыт → налог (каждый 5-й) в казну КАК ДИОРИТ
 *                 → остальные 4 блока игроку КАК БУЛЫЖНИК
 *   
 *   Андезит добыт → налог в казну КАК АНДЕЗИТ
 *                  → остальные 4 блока игроку КАК БУЛЫЖНИК
 *   
 *   Булыжник добыт → налог в казну КАК БУЛЫЖНИК
 *                   → остальные 4 блока игроку КАК БУЛЫЖНИК
 *   
 *   Камень/гранит/др → налог в казну КАК БУЛЫЖНИК
 *                     → остальные 4 блока игроку КАК БУЛЫЖНИК
 */
public class MineBlockBreakListener implements Listener {

    private final MinePlugin plugin;
    private final MineLevelGUI mineLevelGUI;
    private final TaxTracker taxTracker;

    // Границы зоны шахты уровня 1
    private static final double MINE_MIN_X = -280.0;
    private static final double MINE_MAX_X = -180.0;
    private static final double MINE_MIN_Y = 30.0;
    private static final double MINE_MAX_Y = 50.0;
    private static final double MINE_MIN_Z = -120.0;
    private static final double MINE_MAX_Z = -20.0;

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

        if (!mineLevelGUI.isPlayerInMine(uuid)) {
            return;
        }

        double bx = event.getBlock().getX();
        double by = event.getBlock().getY();
        double bz = event.getBlock().getZ();

        if (bx < MINE_MIN_X || bx > MINE_MAX_X
                || by < MINE_MIN_Y || by > MINE_MAX_Y
                || bz < MINE_MIN_Z || bz > MINE_MAX_Z) {
            return;
        }

        Material brokenBlock = event.getBlock().getType();

        // Отменяем стандартный дроп
        event.setDropItems(false);

        // ===================================================
        // СЛУЧАЙ 1: Ценные руды -> полностью в казну
        // ===================================================
        if (TaxUtils.isTreasuryOre(brokenBlock)) {
            plugin.getKaznaManager().addItem(brokenBlock, 1);

            player.sendMessage(Component.text("[Шахта] ")
                    .color(NamedTextColor.DARK_GREEN)
                    .append(Component.text(TaxUtils.getRussianName(brokenBlock))
                            .color(NamedTextColor.GOLD))
                    .append(Component.text(" отправлена в казну города!")
                            .color(NamedTextColor.YELLOW)));
            return;
        }

        // ===================================================
        // СЛУЧАЙ 2: Диорит или Андезит
        // Налог → в казну КАК ДИОРИТ/АНДЕЗИТ
        // Остаток → игроку КАК БУЛЫЖНИК
        // ===================================================
        if (brokenBlock == Material.DIORITE || brokenBlock == Material.ANDESITE) {

            boolean isTaxBlock = taxTracker.incrementAndCheckTax(uuid);

            if (isTaxBlock) {
                // Налог: в казну как оригинальный блок (диорит/андезит)
                plugin.getKaznaManager().addItem(brokenBlock, 1);

                player.sendMessage(Component.text("[Налог] ")
                        .color(NamedTextColor.RED)
                        .append(Component.text(TaxUtils.getRussianName(brokenBlock)
                                        + " удержан как налог 20%")
                                .color(NamedTextColor.GRAY)));
            } else {
                // Игроку: выдаём БУЛЫЖНИК (не диорит/андезит)
                giveItemToPlayer(player, Material.COBBLESTONE, 1);

                player.sendActionBar(Component.text(
                                TaxUtils.getRussianName(brokenBlock) + " → Булыжник")
                        .color(NamedTextColor.YELLOW));
            }
            return;
        }

        // ===================================================
        // СЛУЧАЙ 3: Булыжник
        // Налог → в казну как булыжник
        // Остаток → игроку как булыжник
        // ===================================================
        if (brokenBlock == Material.COBBLESTONE) {

            boolean isTaxBlock = taxTracker.incrementAndCheckTax(uuid);

            if (isTaxBlock) {
                plugin.getKaznaManager().addItem(Material.COBBLESTONE, 1);

                player.sendMessage(Component.text("[Налог] ")
                        .color(NamedTextColor.RED)
                        .append(Component.text("Булыжник удержан как налог 20%")
                                .color(NamedTextColor.GRAY)));
            } else {
                giveItemToPlayer(player, Material.COBBLESTONE, 1);
            }
            return;
        }

        // ===================================================
        // СЛУЧАЙ 4: Любой другой блок (камень, гранит и т.д.)
        // Всё превращается в булыжник
        // Налог → в казну как булыжник
        // Остаток → игроку как булыжник
        // ===================================================
        boolean isTaxBlock = taxTracker.incrementAndCheckTax(uuid);

        if (isTaxBlock) {
            plugin.getKaznaManager().addItem(Material.COBBLESTONE, 1);

            player.sendMessage(Component.text("[Налог] ")
                    .color(NamedTextColor.RED)
                    .append(Component.text("Блок удержан как налог 20% (Булыжник)")
                            .color(NamedTextColor.GRAY)));
        } else {
            giveItemToPlayer(player, Material.COBBLESTONE, 1);

            if (brokenBlock != Material.COBBLESTONE) {
                player.sendActionBar(Component.text(
                                TaxUtils.getRussianName(brokenBlock) + " → Булыжник")
                        .color(NamedTextColor.YELLOW));
            }
        }
    }

    /**
     * Выдать предмет игроку. Если инвентарь полон — дропнуть.
     */
    private void giveItemToPlayer(Player player, Material material, int amount) {
        Map<Integer, ItemStack> overflow = player.getInventory()
                .addItem(new ItemStack(material, amount));

        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }
}
