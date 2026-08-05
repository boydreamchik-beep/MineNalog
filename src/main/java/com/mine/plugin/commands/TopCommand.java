package com.mine.plugin.commands;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.managers.AchievementManager;
import com.mine.plugin.managers.LeaderboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TopCommand implements CommandExecutor, TabCompleter {

    private final MinePlugin plugin;
    private final LeaderboardManager leaderboardManager;

    public TopCommand(MinePlugin plugin) {
        this.plugin = plugin;
        this.leaderboardManager = new LeaderboardManager(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        int topCount = 10;

        if (args.length > 1) {
            try {
                topCount = Integer.parseInt(args[1]);
                topCount = Math.max(1, Math.min(50, topCount));
            } catch (NumberFormatException ignored) {
            }
        }

        switch (sub) {
            case "mine" -> leaderboardManager.showMiningTop(player, topCount);
            case "tax" -> leaderboardManager.showTaxTop(player, topCount);
            case "visit" -> leaderboardManager.showVisitTop(player, topCount);
            case "ach" -> leaderboardManager.showAchievementTop(player, topCount);
            default -> showHelp(player);
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("╔══════════════════════════════════╗")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  📊 ТАБЛИЦЫ ЛИДЕРОВ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("║")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  /top mine [кол-во]")
                .color(NamedTextColor.WHITE)
                .append(Component.text(" — топ шахтёров")
                        .color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("║  /top tax [кол-во]")
                .color(NamedTextColor.WHITE)
                .append(Component.text(" — топ налогоплательщиков")
                        .color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("║  /top visit [кол-во]")
                .color(NamedTextColor.WHITE)
                .append(Component.text(" — топ посещений шахты")
                        .color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("║  /top ach [кол-во]")
                .color(NamedTextColor.WHITE)
                .append(Component.text(" — топ по достижениям")
                        .color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("╚══════════════════════════════════╝")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                  @NotNull Command command,
                                                  @NotNull String alias,
                                                  @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("mine");
            completions.add("tax");
            completions.add("visit");
            completions.add("ach");
            return completions;
        }
        return Collections.emptyList();
    }
}
