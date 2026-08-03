package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Кредитная система.
 * 
 * - Максимум: 100 стаков булыжника (6400 шт.)
 * - Ставка: 3%
 * - Напоминание: каждые 3 дня (в реальном времени)
 * - Проценты начисляются каждые 3 дня
 * 
 * Данные хранятся в credits.yml
 */
public class CreditManager {

    private final MinePlugin plugin;
    private final File creditFile;
    private FileConfiguration creditConfig;

    // UUID -> данные кредита
    private final Map<UUID, CreditData> credits = new HashMap<>();

    // Максимальный кредит: 100 стаков = 6400 булыжников
    public static final int MAX_CREDIT = 6400;

    // Ставка 3%
    public static final double INTEREST_RATE = 0.03;

    // Интервал напоминания: 3 дня в тиках (20 * 60 * 60 * 24 * 3)
    // Для тестирования можно уменьшить
    public static final long REMINDER_INTERVAL_TICKS = 20L * 60 * 60 * 24 * 3;

    // Для тестирования: каждые 10 минут
    // public static final long REMINDER_INTERVAL_TICKS = 20L * 60 * 10;

    private BukkitTask reminderTask;

    public CreditManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.creditFile = new File(plugin.getDataFolder(), "credits.yml");
    }

    public void load() {
        if (!creditFile.exists()) {
            try {
                creditFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать credits.yml!");
                e.printStackTrace();
            }
        }

        creditConfig = YamlConfiguration.loadConfiguration(creditFile);
        credits.clear();

        if (creditConfig.contains("credits")) {
            var section = creditConfig.getConfigurationSection("credits");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        int amount = section.getInt(uuidStr + ".amount", 0);
                        long takenTime = section.getLong(uuidStr + ".taken_time", 0);
                        long lastReminder = section.getLong(uuidStr + ".last_reminder", 0);
                        int totalPaid = section.getInt(uuidStr + ".total_paid", 0);

                        if (amount > 0) {
                            CreditData data = new CreditData(amount, takenTime,
                                    lastReminder, totalPaid);
                            credits.put(uuid, data);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Ошибка загрузки кредита: " + uuidStr);
                    }
                }
            }
        }

        plugin.getLogger().info("Кредитов загружено: " + credits.size());
    }

    public void save() {
        creditConfig = new YamlConfiguration();

        for (Map.Entry<UUID, CreditData> entry : credits.entrySet()) {
            String path = "credits." + entry.getKey().toString();
            CreditData data = entry.getValue();
            creditConfig.set(path + ".amount", data.amount);
            creditConfig.set(path + ".taken_time", data.takenTime);
            creditConfig.set(path + ".last_reminder", data.lastReminder);
            creditConfig.set(path + ".total_paid", data.totalPaid);
        }

        try {
            creditConfig.save(creditFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить credits.yml!");
            e.printStackTrace();
        }
    }

    /**
     * Запустить периодическое напоминание и начисление процентов
     */
    public void startReminders() {
        // Проверяем каждый час (72000 тиков)
        reminderTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            for (Map.Entry<UUID, CreditData> entry : credits.entrySet()) {
                UUID uuid = entry.getKey();
                CreditData data = entry.getValue();

                // 3 дня в миллисекундах
                long threeDaysMs = 3L * 24 * 60 * 60 * 1000;

                if ((now - data.lastReminder) >= threeDaysMs) {
                    // Начисляем проценты
                    int interest = (int) Math.ceil(data.amount * INTEREST_RATE);
                    data.amount += interest;
                    data.lastReminder = now;

                    // Отправляем напоминание если игрок онлайн
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        sendReminder(player, data, interest);
                    }

                    save();
                }
            }
        }, 72000L, 72000L); // Каждый час
    }

    public void stopReminders() {
        if (reminderTask != null) {
            reminderTask.cancel();
        }
    }

    /**
     * Отправить напоминание игроку
     */
    private void sendReminder(Player player, CreditData data, int interest) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("╔══════════════════════════════╗")
                .color(NamedTextColor.RED));
        player.sendMessage(Component.text("║  НАПОМИНАНИЕ О КРЕДИТЕ!")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("║")
                .color(NamedTextColor.RED));
        player.sendMessage(Component.text("║  Начислены проценты: +" + interest + " булыж.")
                .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("║  Ваш долг: " + data.amount + " булыжников")
                .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("║  (" + (data.amount / 64) + " стаков + "
                        + (data.amount % 64) + " шт.)")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("║")
                .color(NamedTextColor.RED));
        player.sendMessage(Component.text("║  Погасите кредит: /credit pay <сумма>")
                .color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("╚══════════════════════════════╝")
                .color(NamedTextColor.RED));
        player.sendMessage(Component.empty());
    }

    /**
     * Взять кредит
     */
    public TakeResult takeCredit(UUID uuid, int amount) {
        if (amount <= 0) {
            return TakeResult.INVALID_AMOUNT;
        }

        CreditData existing = credits.get(uuid);
        int currentDebt = existing != null ? existing.amount : 0;

        if (currentDebt + amount > MAX_CREDIT) {
            return TakeResult.EXCEEDS_LIMIT;
        }

        if (existing != null) {
            existing.amount += amount;
        } else {
            long now = System.currentTimeMillis();
            credits.put(uuid, new CreditData(amount, now, now, 0));
        }

        save();
        return TakeResult.SUCCESS;
    }

    /**
     * Погасить кредит (списывает булыжник из инвентаря)
     */
    public PayResult payCredit(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        CreditData data = credits.get(uuid);

        if (data == null || data.amount <= 0) {
            return PayResult.NO_CREDIT;
        }

        if (amount <= 0) {
            return PayResult.INVALID_AMOUNT;
        }

        // Ограничиваем суммой долга
        int actualPay = Math.min(amount, data.amount);

        // Проверяем хватает ли булыжника
        int playerCobble = countCobblestone(player);
        if (playerCobble < actualPay) {
            return PayResult.NOT_ENOUGH_COBBLESTONE;
        }

        // Списываем булыжник
        removeCobblestone(player, actualPay);

        // Уменьшаем долг
        data.amount -= actualPay;
        data.totalPaid += actualPay;

        if (data.amount <= 0) {
            credits.remove(uuid);
        }

        save();
        return PayResult.SUCCESS;
    }

    /**
     * Получить данные кредита
     */
    public CreditData getCreditData(UUID uuid) {
        return credits.get(uuid);
    }

    /**
     * Есть ли кредит у игрока
     */
    public boolean hasCredit(UUID uuid) {
        CreditData data = credits.get(uuid);
        return data != null && data.amount > 0;
    }

    /**
     * Выдать булыжник игроку (кредитные средства)
     */
    public void giveCobblestone(Player player, int amount) {
        int fullStacks = amount / 64;
        int remainder = amount % 64;

        for (int i = 0; i < fullStacks; i++) {
            Map<Integer, ItemStack> overflow = player.getInventory()
                    .addItem(new ItemStack(Material.COBBLESTONE, 64));
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        if (remainder > 0) {
            Map<Integer, ItemStack> overflow = player.getInventory()
                    .addItem(new ItemStack(Material.COBBLESTONE, remainder));
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }

    private int countCobblestone(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COBBLESTONE) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeCobblestone(Player player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != Material.COBBLESTONE) continue;
            if (com.mine.plugin.listeners.CompassListener.isMineCompass(item)) continue;

            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
            if (remaining <= 0) break;
        }
    }

    // === Вспомогательные классы и перечисления ===

    public static class CreditData {
        public int amount;          // Текущий долг
        public long takenTime;      // Когда взят кредит
        public long lastReminder;   // Последнее напоминание
        public int totalPaid;       // Всего выплачено

        public CreditData(int amount, long takenTime, long lastReminder, int totalPaid) {
            this.amount = amount;
            this.takenTime = takenTime;
            this.lastReminder = lastReminder;
            this.totalPaid = totalPaid;
        }
    }

    public enum TakeResult {
        SUCCESS,
        EXCEEDS_LIMIT,
        INVALID_AMOUNT
    }

    public enum PayResult {
        SUCCESS,
        NO_CREDIT,
        INVALID_AMOUNT,
        NOT_ENOUGH_COBBLESTONE
    }
}
