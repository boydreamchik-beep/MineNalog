package com.mine.plugin.commands;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.managers.PassportManager;
import com.mine.plugin.managers.PassportManager.PassportData;
import com.mine.plugin.managers.IncomeTracker;
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
import java.util.*;

/**
 * /passport <Фамилия> <Имя> <Отчество> <ДатаРождения> <Пол>
 * /passport — просмотр
 * 
 * Место рождения автоматически: Topicus, Энем
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

        if (args.length == 0) {
            if (pm.hasPassport(uuid)) {
                showPassport(player);
            } else {
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("[Паспорт] У вас нет паспорта!")
                        .color(NamedTextColor.RED));
                player.sendMessage(Component.text("[Паспорт] Получите:")
                        .color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("/passport <Фамилия> <Имя> <Отчество> <ДатаРождения> <Пол>")
                        .color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("Пример: /passport Иванов Иван Иванович 01.01.2000 М")
                        .color(NamedTextColor.DARK_GRAY));
                player.sendMessage(Component.empty());
            }
            return true;
        }

        if (pm.hasPassport(uuid)) {
            player.sendMessage(Component.text("[Паспорт] У вас уже есть паспорт!")
                    .color(NamedTextColor.RED));
            player.sendMessage(Component.text("[Паспорт] /passport — для просмотра")
                    .color(NamedTextColor.GRAY));
            return true;
        }

        // Нужно 5 аргументов
        if (args.length < 5) {
            player.sendMessage(Component.text("[Паспорт] Заполните все поля!")
                    .color(NamedTextColor.RED));
            player.sendMessage(Component.text("/passport <Фамилия> <Имя> <Отчество> <ДатаРождения> <Пол>")
                    .color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Дата: ДД.ММ.ГГГГ | Пол: М или Ж")
                    .color(NamedTextColor.GRAY));
            return true;
        }

        String lastName = capitalize(args[0]);
        String firstName = capitalize(args[1]);
        String middleName = capitalize(args[2]);
        String birthDate = args[3];
        String genderInput = args[4].toUpperCase();

        // Валидация ФИО
        if (!isValidName(lastName) || !isValidName(firstName) || !isValidName(middleName)) {
            player.sendMessage(Component.text("[Паспорт] ФИО может содержать только буквы!")
                    .color(NamedTextColor.RED));
            return true;
        }

        // Валидация даты
        if (!birthDate.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
            player.sendMessage(Component.text("[Паспорт] Неверный формат даты! Используйте: ДД.ММ.ГГГГ")
                    .color(NamedTextColor.RED));
            return true;
        }

        // Валидация пола
        String gender;
        if (genderInput.equals("М") || genderInput.equals("M")) {
            gender = "Мужской";
        } else if (genderInput.equals("Ж") || genderInput.equals("F")) {
            gender = "Женский";
        } else {
            player.sendMessage(Component.text("[Паспорт] Пол: М (мужской) или Ж (женский)")
                    .color(NamedTextColor.RED));
            return true;
        }

        String birthPlace = plugin.getConfigManager().getPassportBirthPlace();

        PassportData data = new PassportData();
        data.lastName = lastName;
        data.firstName = firstName;
        data.middleName = middleName;
        data.birthDate = birthDate;
        data.birthPlace = birthPlace;
        data.gender = gender;
        data.residenceStatus = "В стране";
        data.searchStatus = "Нет";
        data.residence = "Не указано";
        data.playerName = player.getName();
        data.issueDate = System.currentTimeMillis();

        boolean success = pm.issuePassport(uuid, data);

        if (success) {
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("╔══════════════════════════════════╗")
                    .color(NamedTextColor.DARK_GREEN));
            player.sendMessage(Component.text("║    ПАСПОРТ ГРАЖДАНИНА ВЫДАН!")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true));
            player.sendMessage(Component.text("╚══════════════════════════════════╝")
                    .color(NamedTextColor.DARK_GREEN));
            showPassport(player);
        }

        return true;
    }

    private void showPassport(Player player) {
        PassportData data = plugin.getPassportManager().getPassport(player.getUniqueId());
        if (data == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
        IncomeTracker.IncomeData income = plugin.getIncomeTracker()
                .getIncome(player.getUniqueId());

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("┌──────────────────────────────────────┐")
                .color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("│        ПАСПОРТ ГРАЖДАНИНА           │")
                .color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("│        Государство Topicus          │")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("│                                      │")
                .color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("│  Фамилия:    ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.lastName).color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, true)));
        player.sendMessage(Component.text("│  Имя:        ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.firstName).color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, true)));
        player.sendMessage(Component.text("│  Отчество:   ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.middleName).color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, true)));
        player.sendMessage(Component.text("│  Дата рожд.: ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.birthDate).color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("│  Место рожд.: ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.birthPlace).color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("│  Пол:        ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.gender).color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("│                                      │")
                .color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("│  Ник:        ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.playerName).color(NamedTextColor.AQUA)));
        player.sendMessage(Component.text("│  Город:      ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text("Энем").color(NamedTextColor.GREEN)));
        player.sendMessage(Component.text("│  Проживание: ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.residence).color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("│  Дата выдачи: ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(sdf.format(new Date(data.issueDate)))
                        .color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("│                                      │")
                .color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("│  Статус:     ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.residenceStatus).color(NamedTextColor.GOLD)));
        player.sendMessage(Component.text("│  Обыск:      ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text(data.searchStatus).color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("│  Налог оплач.: ")
                .color(NamedTextColor.DARK_RED)
                .append(Component.text((income.taxPaidTotal + income.autoTaxPaid) + " булыж.")
                        .color(NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("└──────────────────────────────────────┘")
                .color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.empty());
    }

    private boolean isValidName(String name) {
        return name.matches("[a-zA-Zа-яА-ЯёЁ]+") && name.length() >= 2 && name.length() <= 20;
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
        return switch (args.length) {
            case 1 -> List.of("<Фамилия>");
            case 2 -> List.of("<Имя>");
            case 3 -> List.of("<Отчество>");
            case 4 -> List.of("<ДД.ММ.ГГГГ>");
            case 5 -> List.of("М", "Ж");
            default -> Collections.emptyList();
        };
    }
}
