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
 * Каждые 3 игровых дня (1 игровой день = 20 мин)
 * забирает 20% булыжника из инвентаря и сундуков.
 */
public class TaxCollector {

    private final MinePlugin plugin;
    private BukkitTask task;

    public TaxCollector(MinePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        boolean enabled = plugin.getConfig().getBoolean("auto-tax.enabled", true);
        if (!enabled) return;

        int intervalDays = plugin.getConfig().getInt("auto-tax.interval-game-days", 3);
        // 1 игровой день = 24000 тиков
        long intervalTicks = (long) intervalDays * 24000L;

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::collectTaxes, intervalTicks, intervalTicks);
        plugin.getLogger().info("Автоналог запущен. Интервал: " + intervalDays + " игровых дней.");
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void collectTaxes() {
        double rate = plugin.getConfig().getDouble("auto-tax.rate", 0.20);
        String currencyStr = plugin.getConfig().getString("auto-tax.currency", "COBBLESTONE");
        Material currency;
        try {
            currency = Material.valueOf(currencyStr);
        } catch (Exception e) {
            currency = Material.COBBLESTONE;
        }

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
