package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Автоматический налог.
 * Забирает процент булыжника из инвентаря и сундуков.
 * Интервал в игровых днях (настраивается через ConfigManager).
 */
public class TaxCollector {

    private final MinePlugin plugin;
    private BukkitTask task;

    public TaxCollector(MinePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isAutoTaxEnabled()) return;

        int intervalDays = cfg.getAutoTaxIntervalGameDays();
        // Используем игровые дни в тиках из ConfigManager
        long intervalTicks = (long) intervalDays * cfg.getGameDayTicks();

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::collectTaxes, intervalTicks, intervalTicks);
        plugin.getLogger().info("Автоналог запущен. Интервал: " + intervalDays + " игровых дней.");
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void collectTaxes() {
        ConfigManager cfg = plugin.getConfigManager();
        double rate = cfg.getAutoTaxRate();
        Material currency = cfg.getAutoTaxCurrency();

        for (Player player : Bukkit.getOnlinePlayers()) {
            int totalCobble = ChestScanner.countTotalMaterial(player, currency);

            if (totalCobble <= 0) continue;

            int tax = (int) Math.ceil(totalCobble * rate);
            if (tax <= 0) continue;

            // Удаляем из инвентаря и сундуков
            int notRemoved = ChestScanner.removeMaterialFromAll(player, currency, tax);
            int actuallyRemoved = tax - notRemoved;

            if (actuallyRemoved > 0) {
                // В казну
                plugin.getKaznaManager().addItem(currency, actuallyRemoved);

                // Учёт
                plugin.getIncomeTracker().recordAutoTax(player.getUniqueId(), actuallyRemoved);

                // Уведомление
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("╔══════════════════════════════╗")
                        .color(NamedTextColor.RED));
                player.sendMessage(Component.text("║  АВТОНАЛОГ")
                        .color(NamedTextColor.RED));
                player.sendMessage(Component.text("║  Списано: " + actuallyRemoved + " булыжников")
                        .color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("║  (" + (int)(rate * 100) + "% от " + totalCobble + ")")
                        .color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("╚══════════════════════════════╝")
                        .color(NamedTextColor.RED));
            }
        }
    }
}
