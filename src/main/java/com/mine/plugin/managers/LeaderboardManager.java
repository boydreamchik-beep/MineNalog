package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Менеджер таблиц лидеров.
 * Показывает топ игроков по различным критериям.
 */
public class LeaderboardManager {

    private final MinePlugin plugin;

    public LeaderboardManager(MinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Показать топ по добытым блокам
     */
    public void showMiningTop(Player player, int topCount) {
        List<PlayerStat> stats = new ArrayList<>();

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            UUID uuid = offlinePlayer.getUniqueId();
            IncomeTracker.IncomeData income = plugin.getIncomeTracker().getIncome(uuid);

            if (income.minedTotal > 0) {
                String name = offlinePlayer.getName();
                if (name == null) name = uuid.toString().substring(0, 8);
                stats.add(new PlayerStat(name, income.minedTotal));
            }
        }

        stats.sort((a, b) -> Integer.compare(b.value, a.value));
        showTop(player, "🏆 ТОП ШАХТЁРОВ", "блоков добыто", stats, topCount, NamedTextColor.GREEN);
    }

    /**
     * Показать топ по уплаченным налогам
     */
    public void showTaxTop(Player player, int topCount) {
        List<PlayerStat> stats = new ArrayList<>();

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            UUID uuid = offlinePlayer.getUniqueId();
            IncomeTracker.IncomeData income = plugin.getIncomeTracker().getIncome(uuid);
            int totalTax = income.taxPaidTotal + income.autoTaxPaid;

            if (totalTax > 0) {
                String name = offlinePlayer.getName();
                if (name == null) name = uuid.toString().substring(0, 8);
                stats.add(new PlayerStat(name, totalTax));
            }
        }

        stats.sort((a, b) -> Integer.compare(b.value, a.value));
        showTop(player, "💰 ТОП НАЛОГОПЛАТЕЛЬЩИКОВ", "булыжника уплачено", stats, topCount, NamedTextColor.GOLD);
    }

    /**
     * Показать топ по посещениям шахты
     */
    public void showVisitTop(Player player, int topCount) {
        List<PlayerStat> stats = new ArrayList<>();

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            UUID uuid = offlinePlayer.getUniqueId();
            int visits = plugin.getIncomeTracker().getMineVisits(uuid);

            if (visits > 0) {
                String name = offlinePlayer.getName();
                if (name == null) name = uuid.toString().substring(0, 8);
                stats.add(new PlayerStat(name, visits));
            }
        }

        stats.sort((a, b) -> Integer.compare(b.value, a.value));
        showTop(player, "⚒ ТОП ПОСЕЩЕНИЙ ШАХТЫ", "посещений", stats, topCount, NamedTextColor.AQUA);
    }

    /**
     * Показать топ по достижениям
     */
    public void showAchievementTop(Player player, int topCount) {
        List<PlayerStat> stats = new ArrayList<>();

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            UUID uuid = offlinePlayer.getUniqueId();
            int count = plugin.getAchievementManager().getUnlockedCount(uuid);

            if (count > 0) {
                String name = offlinePlayer.getName();
                if (name == null) name = uuid.toString().substring(0, 8);
                stats.add(new PlayerStat(name, count));
            }
        }

        stats.sort((a, b) -> Integer.compare(b.value, a.value));
        showTop(player, "🏆 ТОП ПО ДОСТИЖЕНИЯМ", "достижений", stats, topCount, NamedTextColor.YELLOW);
    }

    private void showTop(Player player, String title, String unit,
                         List<PlayerStat> stats, int topCount, NamedTextColor color) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("╔══════════════════════════════════╗")
                .color(color));
        player.sendMessage(Component.text("║  " + title)
                .color(color)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("║")
                .color(color));

        int count = Math.min(topCount, stats.size());
        if (count == 0) {
            player.sendMessage(Component.text("║  Пока нет данных")
                    .color(NamedTextColor.GRAY));
        } else {
            for (int i = 0; i < count; i++) {
                PlayerStat stat = stats.get(i);
                NamedTextColor medalColor;
                String medal;

                if (i == 0) {
                    medalColor = NamedTextColor.GOLD;
                    medal = "🥇";
                } else if (i == 1) {
                    medalColor = NamedTextColor.WHITE;
                    medal = "🥈";
                } else if (i == 2) {
                    medalColor = NamedTextColor.YELLOW;
                    medal = "🥉";
                } else {
                    medalColor = NamedTextColor.GRAY;
                    medal = "  " + (i + 1) + ".";
                }

                player.sendMessage(Component.text("║  " + medal + " ")
                        .color(medalColor)
                        .append(Component.text(stat.name)
                                .color(NamedTextColor.WHITE)
                                .decoration(TextDecoration.BOLD, true))
                        .append(Component.text(" — " + stat.value + " " + unit)
                                .color(NamedTextColor.GRAY)));
            }
        }

        player.sendMessage(Component.text("╚══════════════════════════════════╝")
                .color(color));
        player.sendMessage(Component.empty());
    }

    private static class PlayerStat {
        final String name;
        final int value;

        PlayerStat(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}
