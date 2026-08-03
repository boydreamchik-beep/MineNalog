package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Табло справа на экране.
 * Показывает:
 * - Ник игрока
 * - Пинг (задержка)
 * - Государство: Topicus
 * - Город: Энем
 * - Статус проживания: (пусто)
 *
 * Обновляется каждые 2 секунды.
 */
public class ScoreboardManager implements Listener {

    private final MinePlugin plugin;
    private BukkitTask updateTask;

    public ScoreboardManager(MinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Запустить обновление табло
     */
    public void start() {
        // Обновляем каждые 40 тиков (2 секунды)
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateScoreboard(player);
            }
        }, 20L, 40L);
    }

    /**
     * Остановить обновление
     */
    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
        }
    }

    /**
     * Создать/обновить табло для игрока
     */
    public void updateScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective obj = board.registerNewObjective(
                "mineinfo",
                Criteria.DUMMY,
                Component.text("✦ Topicus ✦")
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, true)
        );

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Строки табло (счёт = порядок, больше = выше)
        // Используем пробелы для уникальности строк

        // Линия сверху
        setLine(obj, "§8§m----------", 10);

        // Ник
        setLine(obj, "§f⚒ Ник: §a" + player.getName(), 9);

        // Пустая строка
        setLine(obj, " ", 8);

        // Пинг
        int ping = player.getPing();
        String pingColor;
        if (ping < 50) {
            pingColor = "§a"; // зелёный
        } else if (ping < 100) {
            pingColor = "§e"; // жёлтый
        } else if (ping < 200) {
            pingColor = "§6"; // оранжевый
        } else {
            pingColor = "§c"; // красный
        }
        setLine(obj, "§f⏱ Пинг: " + pingColor + ping + " мс", 7);

        // Пустая строка
        setLine(obj, "  ", 6);

        // Государство
        setLine(obj, "§f🏛 Гос-во: §6Topicus", 5);

        // Город
        setLine(obj, "§f🏠 Город: §bЭнем", 4);

        // Пустая строка
        setLine(obj, "   ", 3);

        // Статус проживания
        setLine(obj, "§f📋 Статус: §7—", 2);

        // Линия снизу
        setLine(obj, "§8§m-----------", 1);

        player.setScoreboard(board);
    }

    /**
     * Установить строку в табло
     */
    private void setLine(Objective obj, String text, int score) {
        Score line = obj.getScore(text);
        line.setScore(score);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Создаём табло через 1 секунду после входа
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                updateScoreboard(event.getPlayer());
            }
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Очищаем табло
        event.getPlayer().setScoreboard(
                Bukkit.getScoreboardManager().getNewScoreboard());
    }
}
