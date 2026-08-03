package com.mine.plugin.commands;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.managers.PassportManager;
import com.mine.plugin.managers.PassportManager.PassportData;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Команда /passport
 * 
 * /passport <Фамилия> <Имя> <Отчество>  — получить паспорт (один раз)
 * /passport                              — просмотреть свой паспорт
 */
public class PassportCommand implements CommandExecutor, TabCompleter {

    private final MinePlugin plugin;

    public PassportCommand(MinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }

        UUID uuid = player.getUniqueId();
        PassportManager pm = plugin.getPassportManager();

        // Если нет аргументов — показать паспорт
        if (args.length == 0) {
            if (pm.hasPassport(uuid)) {
                showPassport(player);
            } else {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("[Паспорт] У вас нет паспорта!")
                        .color(NamedTextColor.RED));
                player.sendMessage(Component.text("[Паспорт] Получите: /passport <Фамилия> <Имя> <Отчество>")
                        .color(NamedTextColor.YELLOW));
                player.sendMessage(Component.empty());
            }
            return true;
        }

        // Если уже есть паспорт — нельзя получить снова
        if (pm.hasPassport(uuid)) {
            player.sendMessage(Component.text("[Паспорт] У вас уже есть паспорт!")
                    .color(NamedTextColor.RED));
            player.sendMessage(Component.text("[Паспорт] Используйте /passport для просмотра")
                    .color(NamedTextColor.GRAY));
            return true;
        }

        // Нужно 3 аргумента: Фамилия Имя Отчество
        if (args.length < 3) {
            player.sendMessage(Component.text("[Паспорт] Укажите полное ФИО!")
                    .color(NamedTextColor.RED));
            player.sendMessage(Component.text("[Паспорт] /passport <Фамилия> <Имя> <Отчество>")
                    .color(NamedTextColor.YELLOW));
            return true;
        }

        String lastName = args[0];
        String firstName = args[1];
        String middleName = args[2];

        // Валидация: только буквы
        if (!isValidName(lastName) || !isValidName(firstName) || !isValidName(middleName)) {
            player.sendMessage(Component.text("[Паспорт] ФИО может содержать только буквы!")
                    .color(NamedTextColor.RED));
            return true;
        }

        // Валидация: длина
        if (lastName.length() < 2 || firstName.length() < 2 || middleName.length() < 2) {
            player.sendMessage(Component.text("[Паспорт] Каждое поле минимум 2 символа!")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (lastName.length() > 20 || firstName.length() > 20 || middleName.length() > 20) {
            player.sendMessage(Component.text("[Паспорт] Каждое поле максимум 20 символов!")
                    .color(NamedTextColor.RED));
            return true;
        }

        // Первая буква заглавная
        lastName = capitalize(lastName);
        firstName = capitalize(firstName);
        middleName = capitalize(middleName);

        // Выдаём паспорт
        boolean success = pm.issuePassport(uuid, player.getName(),
                lastName, firstName, middleName);

        if (success) {
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("╔══════════════════════════════════╗")
                    .color(NamedTextColor.DARK_GREEN));
            player.sendMessage(Component.text("║    ПАСПОРТ ГРАЖДАНИНА ВЫДАН!")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true));
            player.sendMessage(Component.text("╚══════════════════════════════════╝")
                    .color(NamedTextColor.DARK_GREEN));
            player.sendMessage(Component.empty());

            showPassport(player);
        } else {
            player.sendMessage(Component.text("[Паспорт] Ошибка выдачи!")
                    .color(NamedTextColor.RED));
        }

        return true;
    }

    private void showPassport(Player player) {
        PassportData data = plugin.getPassportManager().getPassport(player.getUniqueId());
        if (data == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("┌──────────────────────────────────┐")
                .color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("│      ПАСПОРТ ГРАЖДАНИНА         │")
                .color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("│      Государство Topicus        │")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("│                                  │")
                .color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("│  Фамилия:  ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.lastName)
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, true)));
        player.sendMessage(Component.text("│  Имя:      ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.firstName)
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, true)));
        player.sendMessage(Component.text("│  Отчество: ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.middleName)
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, true)));
        player.sendMessage(Component.text("│                                  │")
                .color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("│  Ник: ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.playerName)
                        .color(NamedTextColor.AQUA)));
        player.sendMessage(Component.text("│  Город: ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text("Энем")
                        .color(NamedTextColor.GREEN)));
        player.sendMessage(Component.text("│  Дата выдачи: ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(sdf.format(new Date(data.issueDate)))
                        .color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("│                                  │")
                .color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("│  Статус: ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text("Гражданин Topicus")
                        .color(NamedTextColor.GOLD)));
        player.sendMessage(Component.text("└──────────────────────────────────┘")
                .color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.empty());
    }

    private boolean isValidName(String name) {
        // Разрешаем кириллицу и латиницу
        return name.matches("[a-zA-Zа-яА-ЯёЁ]+");
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                  @NotNull Command command,
                                                  @NotNull String alias,
                                                  @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("<Фамилия>");
        } else if (args.length == 2) {
            completions.add("<Имя>");
        } else if (args.length == 3) {
            completions.add("<Отчество>");
        }

        return completions;
    }
}
