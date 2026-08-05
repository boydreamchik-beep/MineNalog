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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardManager implements Listener {

    private final MinePlugin plugin;
    private BukkitTask updateTask;

    // Сериализатор для перевода & кодов в Component
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    // Храним Scoreboard для каждого игрока (переиспользуем)
    private final Map<UUID, Scoreboard> playerBoards = new HashMap<>();
    // Храним Objective для каждого игрока
    private final Map<UUID, Objective> playerObjectives = new HashMap<>();

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
        playerBoards.clear();
        playerObjectives.clear();
    }

    public void updateScoreboard(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isScoreboardEnabled()) return;

        UUID uuid = player.getUniqueId();

        // Создаём scoreboard один раз для каждого игрока
        Scoreboard board = playerBoards.get(uuid);
        Objective obj = playerObjectives.get(uuid);

        if (board == null || obj == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            Component titleComponent = LEGACY.deserialize(cfg.getScoreboardTitle());
            obj = board.registerNewObjective("mineinfo", Criteria.DUMMY, titleComponent);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            playerBoards.put(uuid, board);
            playerObjectives.put(uuid, obj);
            player.setScoreboard(board);
        }

        List<String> lines = cfg.getScoreboardLines();

        // Очищаем старые записи
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

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

            // Переводим & коды в legacy § строку через тот же сериализатор
            Component lineComponent = LEGACY.deserialize(processed);
            String legacyText = LEGACY.serialize(lineComponent);

            // Уникальность строк через пробелы в конце
            StringBuilder unique = new StringBuilder(legacyText);
            while (board.getEntries().contains(unique.toString())) {
                unique.append(" ");
            }

            obj.getScore(unique.toString()).setScore(score--);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline()) {
                // При входе создаём новый scoreboard
                removePlayerBoard(player.getUniqueId());
                updateScoreboard(player);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePlayerBoard(event.getPlayer().getUniqueId());
        event.getPlayer().setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void removePlayerBoard(UUID uuid) {
        playerBoards.remove(uuid);
        playerObjectives.remove(uuid);
    }
}
