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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MineBlockBreakListener implements Listener {

    private final MinePlugin plugin;
    private final MineLevelGUI mineLevelGUI;
    private final TaxTracker taxTracker;

    /*
     * Границы зоны шахты уровня 1.
     * ВАЖНО: Настрой эти координаты под свою карту!
     * Шахта должна полностью попадать в эти рамки.
     */
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

        // Проверяем: игрок в шахте?
        if (!mineLevelGUI.isPlayerInMine(uuid)) {
            return;
        }

        // Проверяем: блок внутри зоны шахты?
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

        // ==========================================
        // СЛУЧАЙ 1: Ценные руды -> полностью в казну
        // ==========================================
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

        // ==========================================
        // СЛУЧАЙ 2 и 3: Обычные блоки с налогом 20%
        // ==========================================

        // Определяем что получит игрок
        Material resultMaterial;

        if (TaxUtils.isAllowedMaterial(brokenBlock)) {
            // Булыжник, диорит, андезит — остаются как есть
            resultMaterial = brokenBlock;
        } else {
            // Всё остальное — превращается в булыжник
            resultMaterial = Material.COBBLESTONE;
        }

        // Проверяем налог (каждый 5-й блок)
        boolean isTaxBlock = taxTracker.incrementAndCheckTax(uuid);

        if (isTaxBlock) {
            // Этот блок — налог, уходит в казну
            plugin.getKaznaManager().addItem(resultMaterial, 1);

            player.sendMessage(Component.text("[Налог] ")
                    .color(NamedTextColor.RED)
                    .append(Component.text("Блок удержан как налог 20% (")
                            .color(NamedTextColor.GRAY))
                    .append(Component.text(TaxUtils.getRussianName(resultMaterial))
                            .color(NamedTextColor.WHITE))
                    .append(Component.text(")")
                            .color(NamedTextColor.GRAY)));
        } else {
            // Блок достаётся игроку
            Map<Integer, ItemStack> overflow = player.getInventory()
                    .addItem(new ItemStack(resultMaterial, 1));

            // Если инвентарь полон — дропаем на землю
            if (!overflow.isEmpty()) {
                for (ItemStack item : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }

            // Если блок был сконвертирован — уведомляем (но не спамим)
            if (!TaxUtils.isAllowedMaterial(brokenBlock)
                    && brokenBlock != Material.COBBLESTONE) {
                // Показываем только в ActionBar чтобы не спамить чат
                player.sendActionBar(Component.text(
                                TaxUtils.getRussianName(brokenBlock) + " -> Булыжник")
                        .color(NamedTextColor.YELLOW));
            }
        }
    }
}
