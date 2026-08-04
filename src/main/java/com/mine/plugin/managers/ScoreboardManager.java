package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

        String title = ChatColor.translateAlternateColorCodes('&', cfg.getScoreboardTitle());

        Objective obj = board.registerNewObjective("mineinfo", Criteria.DUMMY,
                net.kyori.adventure.text.Component.text(title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = cfg.getScoreboardLines();
        int score = lines.size();

        for (String line : lines) {
            // Замена переменных
            int ping = player.getPing();
            String pingColor;
            if (ping < 50) pingColor = "§a";
            else if (ping < 100) pingColor = "§e";
            else if (ping < 200) pingColor = "§6";
            else pingColor = "§c";

            line = line.replace("{player}", player.getName());
            line = line.replace("{ping}", String.valueOf(ping));
            line = line.replace("{ping_color}", pingColor);
            line = ChatColor.translateAlternateColorCodes('&', line);

            // Уникальность строк (Minecraft требует уникальные строки)
            StringBuilder uniqueLine = new StringBuilder(line);
            while (board.getEntries().contains(uniqueLine.toString())) {
                uniqueLine.append("§r");
            }

            obj.getScore(uniqueLine.toString()).setScore(score--);
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
