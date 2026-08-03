package com.mine.plugin.commands;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.managers.CreditManager;
import com.mine.plugin.managers.CreditManager.CreditData;
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
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Команда /credit
 * 
 * /credit take <количество>  — взять кредит (в штуках булыжника)
 * /credit take <стаки>s      — взять кредит (в стаках)
 * /credit info               — информация о кредите
 * /credit pay <количество>   — погасить кредит
 * /credit pay all             — погасить весь кредит
 */
public class CreditCommand implements CommandExecutor, TabCompleter {

    private final MinePlugin plugin;

    public CreditCommand(MinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "take" -> handleTake(player, args);
            case "info" -> handleInfo(player);
            case "pay" -> handlePay(player, args);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleTake(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("[Кредит] Укажите сумму: /credit take <количество>")
                    .color(NamedTextColor.RED));
            player.sendMessage(Component.text("[Кредит] Или в стаках: /credit take <число>s")
                    .color(NamedTextColor.RED));
            return;
        }

        CreditManager cm = plugin.getCreditManager();
        UUID uuid = player.getUniqueId();

        // Проверяем паспорт
        if (!plugin.getPassportManager().hasPassport(uuid)) {
            player.sendMessage(Component.text("[Кредит] Для получения кредита нужен паспорт!")
                    .color(NamedTextColor.RED));
            player.sendMessage(Component.text("[Кредит] Получите паспорт: /passport <Фамилия> <Имя> <Отчество>")
                    .color(NamedTextColor.YELLOW));
            return;
        }

        String amountStr = args[1];
        int amount;

        try {
            if (amountStr.toLowerCase().endsWith("s")) {
                // Стаки
                int stacks = Integer.parseInt(amountStr.substring(0, amountStr.length() - 1));
                amount = stacks * 64;
            } else {
                amount = Integer.parseInt(amountStr);
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("[Кредит] Неверная сумма!")
                    .color(NamedTextColor.RED));
            return;
        }

        CreditManager.TakeResult result = cm.takeCredit(uuid, amount);

        switch (result) {
            case SUCCESS -> {
                // Выдаём булыжник
                cm.giveCobblestone(player, amount);

                int totalDebt = cm.getCreditData(uuid).amount;

                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("╔══════════════════════════════╗")
                        .color(NamedTextColor.GREEN));
                player.sendMessage(Component.text("║  КРЕДИТ ОДОБРЕН!")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, true));
                player.sendMessage(Component.text("║")
                        .color(NamedTextColor.GREEN));
                player.sendMessage(Component.text("║  Получено: " + amount + " булыжников")
                        .color(NamedTextColor.WHITE));
                player.sendMessage(Component.text("║  (" + (amount / 64) + " стаков + "
                                + (amount % 64) + " шт.)")
                        .color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("║  Ставка: 3% каждые 3 дня")
                        .color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("║  Общий долг: " + totalDebt + " булыж.")
                        .color(NamedTextColor.RED));
                player.sendMessage(Component.text("╚══════════════════════════════╝")
                        .color(NamedTextColor.GREEN));
                player.sendMessage(Component.empty());
            }
            case EXCEEDS_LIMIT -> {
                CreditData data = cm.getCreditData(uuid);
                int currentDebt = data != null ? data.amount : 0;
                int canTake = CreditManager.MAX_CREDIT - currentDebt;

                player.sendMessage(Component.text("[Кредит] Превышен лимит!")
                        .color(NamedTextColor.RED));
                player.sendMessage(Component.text("[Кредит] Максимум: " + CreditManager.MAX_CREDIT
                                + " (" + (CreditManager.MAX_CREDIT / 64) + " стаков)")
                        .color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("[Кредит] Текущий долг: " + currentDebt)
                        .color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("[Кредит] Можно взять ещё: " + canTake)
                        .color(NamedTextColor.YELLOW));
            }
            case INVALID_AMOUNT -> {
                player.sendMessage(Component.text("[Кредит] Сумма должна быть больше 0!")
                        .color(NamedTextColor.RED));
            }
        }
    }

    private void handleInfo(Player player) {
        CreditManager cm = plugin.getCreditManager();
        UUID uuid = player.getUniqueId();

        if (!cm.hasCredit(uuid)) {
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("╔══════════════════════════════╗")
                    .color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("║  У вас нет кредита!")
                    .color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("║  Взять: /credit take <сумма>")
                    .color(NamedTextColor.GRAY));
            player.sendMessage(Component.text("╚══════════════════════════════╝")
                    .color(NamedTextColor.GREEN));
            player.sendMessage(Component.empty());
            return;
        }

        CreditData data = cm.getCreditData(uuid);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("╔══════════════════════════════╗")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  ИНФОРМАЦИЯ О КРЕДИТЕ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("║")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  Долг: " + data.amount + " булыжников")
                .color(NamedTextColor.RED));
        player.sendMessage(Component.text("║  (" + (data.amount / 64) + " стаков + "
                        + (data.amount % 64) + " шт.)")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("║  Ставка: 3% каждые 3 дня")
                .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("║  Дата взятия: " + sdf.format(new Date(data.takenTime)))
                .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("║  Всего выплачено: " + data.totalPaid)
                .color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("║")
                .color(NamedTextColor.GOLD));

        // Следующее начисление процентов
        long nextReminder = data.lastReminder + (3L * 24 * 60 * 60 * 1000);
        long remaining = nextReminder - System.currentTimeMillis();
        if (remaining > 0) {
            long hours = remaining / (1000 * 60 * 60);
            long minutes = (remaining / (1000 * 60)) % 60;
            player.sendMessage(Component.text("║  Проценты через: " + hours + "ч " + minutes + "мин")
                    .color(NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("║  Проценты: скоро начислятся!")
                    .color(NamedTextColor.RED));
        }

        int nextInterest = (int) Math.ceil(data.amount * CreditManager.INTEREST_RATE);
        player.sendMessage(Component.text("║  Следующие проценты: +" + nextInterest + " булыж.")
                .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("╚══════════════════════════════╝")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
    }

    private void handlePay(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("[Кредит] Укажите сумму: /credit pay <количество>")
                    .color(NamedTextColor.RED));
            player.sendMessage(Component.text("[Кредит] Или: /credit pay all")
                    .color(NamedTextColor.RED));
            return;
        }

        CreditManager cm = plugin.getCreditManager();
        UUID uuid = player.getUniqueId();

        if (!cm.hasCredit(uuid)) {
            player.sendMessage(Component.text("[Кредит] У вас нет кредита!")
                    .color(NamedTextColor.GREEN));
            return;
        }

        int amount;

        if (args[1].equalsIgnoreCase("all")) {
            amount = cm.getCreditData(uuid).amount;
        } else {
            try {
                if (args[1].toLowerCase().endsWith("s")) {
                    int stacks = Integer.parseInt(args[1].substring(0, args[1].length() - 1));
                    amount = stacks * 64;
                } else {
                    amount = Integer.parseInt(args[1]);
                }
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("[Кредит] Неверная сумма!")
                        .color(NamedTextColor.RED));
                return;
            }
        }

        CreditManager.PayResult result = cm.payCredit(player, amount);

        switch (result) {
            case SUCCESS -> {
                CreditData data = cm.getCreditData(uuid);
                int remainingDebt = data != null ? data.amount : 0;

                if (remainingDebt <= 0) {
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("╔══════════════════════════════╗")
                            .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.text("║  КРЕДИТ ПОЛНОСТЬЮ ПОГАШЕН!")
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.BOLD, true));
                    player.sendMessage(Component.text("║  Поздравляем! Вы свободны.")
                            .color(NamedTextColor.WHITE));
                    player.sendMessage(Component.text("╚══════════════════════════════╝")
                            .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.empty());
                } else {
                    player.sendMessage(Component.text("[Кредит] Оплачено! Остаток долга: "
                                    + remainingDebt + " булыжников")
                            .color(NamedTextColor.GREEN));
                }
            }
            case NOT_ENOUGH_COBBLESTONE -> {
                player.sendMessage(Component.text("[Кредит] Не хватает булыжника для оплаты!")
                        .color(NamedTextColor.RED));
            }
            case NO_CREDIT -> {
                player.sendMessage(Component.text("[Кредит] У вас нет кредита!")
                        .color(NamedTextColor.GREEN));
            }
            case INVALID_AMOUNT -> {
                player.sendMessage(Component.text("[Кредит] Неверная сумма!")
                        .color(NamedTextColor.RED));
            }
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("╔══════════════════════════════╗")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  КРЕДИТНАЯ СИСТЕМА")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("║")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  /credit take <сумма>")
                .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("║    Взять кредит (булыжником)")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("║  /credit take <число>s")
                .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("║    Взять кредит (в стаках)")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("║  /credit info")
                .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("║    Информация о кредите")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("║  /credit pay <сумма>")
                .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("║    Погасить кредит")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("║  /credit pay all")
                .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("║    Погасить весь кредит")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("║")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  Макс: 100 стаков (6400 шт.)")
                .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("║  Ставка: 3% каждые 3 дня")
                .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("║  Нужен паспорт: /passport")
                .color(NamedTextColor.RED));
        player.sendMessage(Component.text("╚══════════════════════════════╝")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                  @NotNull Command command,
                                                  @NotNull String alias,
                                                  @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("take");
            completions.add("info");
            completions.add("pay");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("take")) {
                completions.add("64");
                completions.add("640");
                completions.add("1s");
                completions.add("10s");
                completions.add("100s");
            } else if (args[0].equalsIgnoreCase("pay")) {
                completions.add("64");
                completions.add("640");
                completions.add("all");
            }
        }

        return completions;
    }
}
