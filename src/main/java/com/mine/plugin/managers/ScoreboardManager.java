package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

public class ScoreboardManager implements Listener {

    private final MinePlugin plugin;
    private BukkitTask updateTask;

    // Сериализатор для перевода & кодов в Component
    private static final LegacyComponentSerializer LEGACY = 
            LegacyComponentSerializer.legacyAmpersand();

    public ScoreboardManager(MinePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isScoreboardEnabled()) return;

        int interval = cfg.getScoreboardUpdateInterval();

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateScoreboard(player);
            }
        }, 20L, interval);
    }

    public void stop() {
        if (updateTask != null) updateTask.cancel();
    }

    public void updateScoreboard(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isScoreboardEnabled()) return;

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        // Заголовок как Component
        Component titleComponent = LEGACY.deserialize(cfg.getScoreboardTitle());

        Objective obj = board.registerNewObjective("mineinfo", Criteria.DUMMY, titleComponent);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = cfg.getScoreboardLines();
        int score = lines.size();

        for (String line : lines) {
            int ping = player.getPing();
            String pingColor;
            if (ping < 50) pingColor = "&a";
            else if (ping < 100) pingColor = "&e";
            else if (ping < 200) pingColor = "&6";
            else pingColor = "&c";

            String processed = line
                    .replace("{player}", player.getName())
                    .replace("{ping}", String.valueOf(ping))
                    .replace("{ping_color}", pingColor);

            // Переводим & коды в § через LegacyComponentSerializer
            Component lineComponent = LEGACY.deserialize(processed);
            String legacyText = net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', processed);

            // Уникальность строк
            StringBuilder unique = new StringBuilder(legacyText);
            while (board.getEntries().contains(unique.toString())) {
                unique.append("§r");
            }

            obj.getScore(unique.toString()).setScore(score--);
        }

        player.setScoreboard(board);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                updateScoreboard(event.getPlayer());
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.getPlayer().setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }
}
