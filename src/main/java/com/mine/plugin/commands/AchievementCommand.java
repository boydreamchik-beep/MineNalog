package com.mine.plugin.commands;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.managers.AchievementManager;
import com.mine.plugin.managers.AchievementManager.Achievement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

public class AchievementCommand implements CommandExecutor {

    private final MinePlugin plugin;

    public AchievementCommand(MinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }

        showAchievements(player);
        return true;
    }

    private void showAchievements(Player player) {
        UUID uuid = player.getUniqueId();
        AchievementManager achievementManager = plugin.getAchievementManager();
        Set<String> unlocked = achievementManager.getUnlockedAchievements(uuid);
        int total = achievementManager.getTotalAchievements();
        int unlockedCount = unlocked.size();

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("╔══════════════════════════════════╗")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  🏆 ВАШИ ДОСТИЖЕНИЯ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("║  Прогресс: " + unlockedCount + " / " + total)
                .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("║")
                .color(NamedTextColor.GOLD));

        for (Achievement achievement : achievementManager.getAllAchievements()) {
            boolean isUnlocked = unlocked.contains(achievement.id);

            String status = isUnlocked ? "✔" : "✖";
            NamedTextColor statusColor = isUnlocked ? NamedTextColor.GREEN : NamedTextColor.RED;

            player.sendMessage(Component.text("║  " + status + " ")
                    .color(statusColor)
                    .append(Component.text(achievement.name)
                            .color(isUnlocked ? NamedTextColor.WHITE : NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, isUnlocked))
                    .append(Component.text(" (" + achievement.reward + ")")
                            .color(NamedTextColor.GRAY)));

            if (isUnlocked) {
                player.sendMessage(Component.text("║    └─ " + achievement.description)
                        .color(NamedTextColor.GRAY));
            }
        }

        player.sendMessage(Component.text("╚══════════════════════════════════╝")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
    }
}
