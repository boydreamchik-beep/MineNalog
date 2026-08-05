package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.utils.TaxUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Система достижений.
 * Отслеживает прогресс игроков и награждает их за достижения.
 */
public class AchievementManager {

    private final MinePlugin plugin;
    private final File achievementsFile;
    private final Map<UUID, Set<String>> unlockedAchievements = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> progress = new HashMap<>();
    private BukkitTask autoSaveTask;
    private volatile boolean dirty = false;

    // Список всех достижений
    private final List<Achievement> achievements = new ArrayList<>();

    public AchievementManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.achievementsFile = new File(plugin.getDataFolder(), "achievements.yml");
        initAchievements();
    }

    private void initAchievements() {
        // Достижения связанные с шахтой
        achievements.add(new Achievement("first_mine", "Первый спуск", "Спуститься в шахту", 10));
        achievements.add(new Achievement("miner_100", "Шахтёр", "Добыть 100 блоков", 50));
        achievements.add(new Achievement("miner_1000", "Опытный шахтёр", "Добыть 1000 блоков", 200));
        achievements.add(new Achievement("miner_10000", "Мастер-шахтёр", "Добыть 10000 блоков", 1000));

        // Достижения связанные с налогами
        achievements.add(new Achievement("taxpayer_100", "Налогоплательщик", "Заплатить 100 булыжника налогов", 25));
        achievements.add(new Achievement("taxpayer_1000", "Законопослушный", "Заплатить 1000 булыжника налогов", 100));
        achievements.add(new Achievement("taxpayer_10000", "Меценат", "Заплатить 10000 булыжника налогов", 500));

        // Достижения связанные с имуществом
        achievements.add(new Achievement("landlord", "Землевладелец", "Купить участок", 100));

        // Достижения связанные с кредитами
        achievements.add(new Achievement("first_credit", "Заёмщик", "Взять первый кредит", 10));
        achievements.add(new Achievement("credit_paid", "Надёжный плательщик", "Полностью погасить кредит", 50));

        // Достижения связанные с активностью
        achievements.add(new Achievement("visitor_10", "Завсегдатай", "Посетить шахту 10 раз", 30));
        achievements.add(new Achievement("visitor_50", "Шахтёр-энтузиаст", "Посетить шахту 50 раз", 100));
        achievements.add(new Achievement("visitor_100", "Легенда шахты", "Посетить шахту 100 раз", 500));
    }

    public void load() {
        if (!achievementsFile.getParentFile().exists()) {
            achievementsFile.getParentFile().mkdirs();
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(achievementsFile);

        if (cfg.contains("achievements")) {
            var section = cfg.getConfigurationSection("achievements");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        Set<String> unlocked = new HashSet<>(section.getStringList(uuidStr + ".unlocked"));
                        unlockedAchievements.put(uuid, unlocked);

                        Map<String, Integer> prog = new HashMap<>();
                        var progSection = section.getConfigurationSection(uuidStr + ".progress");
                        if (progSection != null) {
                            for (String key : progSection.getKeys(false)) {
                                prog.put(key, progSection.getInt(key));
                            }
                        }
                        progress.put(uuid, prog);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Битая запись достижений: " + uuidStr);
                    }
                }
            }
        }

        startAutoSave();
    }

    public void startAutoSave() {
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 2400L, 2400L);
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) autoSaveTask.cancel();
    }

    public void save() {
        dirty = false;
        YamlConfiguration cfg = new YamlConfiguration();

        for (var entry : unlockedAchievements.entrySet()) {
            String path = "achievements." + entry.getKey();
            cfg.set(path + ".unlocked", new ArrayList<>(entry.getValue()));

            Map<String, Integer> prog = progress.get(entry.getKey());
            if (prog != null && !prog.isEmpty()) {
                for (var progEntry : prog.entrySet()) {
                    cfg.set(path + ".progress." + progEntry.getKey(), progEntry.getValue());
                }
            }
        }

        try {
            cfg.save(achievementsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить achievements.yml: " + e.getMessage());
        }
    }

    /**
     * Проверить и выдать достижения за посещение шахты
     */
    public void checkMineVisit(UUID uuid) {
        int visits = plugin.getIncomeTracker().getMineVisits(uuid);

        // Первое посещение
        unlockIfConditionMet(uuid, "first_mine", visits >= 1);

        // Посещения 10, 50, 100
        unlockIfConditionMet(uuid, "visitor_10", visits >= 10);
        unlockIfConditionMet(uuid, "visitor_50", visits >= 50);
        unlockIfConditionMet(uuid, "visitor_100", visits >= 100);
    }

    /**
     * Проверить достижения за добычу блоков
     */
    public void checkMining(UUID uuid) {
        IncomeTracker.IncomeData income = plugin.getIncomeTracker().getIncome(uuid);
        int mined = income.minedTotal;

        unlockIfConditionMet(uuid, "miner_100", mined >= 100);
        unlockIfConditionMet(uuid, "miner_1000", mined >= 1000);
        unlockIfConditionMet(uuid, "miner_10000", mined >= 10000);
    }

    /**
     * Проверить достижения за уплату налогов
     */
    public void checkTaxPaid(UUID uuid) {
        IncomeTracker.IncomeData income = plugin.getIncomeTracker().getIncome(uuid);
        int taxPaid = income.taxPaidTotal + income.autoTaxPaid;

        unlockIfConditionMet(uuid, "taxpayer_100", taxPaid >= 100);
        unlockIfConditionMet(uuid, "taxpayer_1000", taxPaid >= 1000);
        unlockIfConditionMet(uuid, "taxpayer_10000", taxPaid >= 10000);
    }

    /**
     * Выдать достижение за покупку участка
     */
    public void unlockLandlord(UUID uuid) {
        unlockAchievement(uuid, "landlord");
    }

    /**
     * Выдать достижение за первый кредит
     */
    public void unlockFirstCredit(UUID uuid) {
        unlockAchievement(uuid, "first_credit");
    }

    /**
     * Выдать достижение за погашение кредита
     */
    public void unlockCreditPaid(UUID uuid) {
        unlockAchievement(uuid, "credit_paid");
    }

    private void unlockIfConditionMet(UUID uuid, String achievementId, boolean condition) {
        if (condition) {
            unlockAchievement(uuid, achievementId);
        }
    }

    private void unlockAchievement(UUID uuid, String achievementId) {
        Set<String> unlocked = unlockedAchievements.computeIfAbsent(uuid, k -> new HashSet<>());

        if (unlocked.contains(achievementId)) {
            return; // Уже разблокировано
        }

        Achievement achievement = getAchievement(achievementId);
        if (achievement == null) return;

        unlocked.add(achievementId);
        dirty = true;

        // Наградить игрока
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            // Звук
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            // Сообщение
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("╔══════════════════════════════╗")
                    .color(NamedTextColor.GOLD));
            player.sendMessage(Component.text("║  🏆 ДОСТИЖЕНИЕ РАЗБЛОКИРОВАНО!")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true));
            player.sendMessage(Component.text("║")
                    .color(NamedTextColor.GOLD));
            player.sendMessage(Component.text("║  " + achievement.name)
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true));
            player.sendMessage(Component.text("║  " + achievement.description)
                    .color(NamedTextColor.GRAY));
            player.sendMessage(Component.text("║  Награда: " + achievement.reward + " булыжника")
                    .color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("╚══════════════════════════════╝")
                    .color(NamedTextColor.GOLD));
            player.sendMessage(Component.empty());

            // Выдать награду
            if (achievement.reward > 0) {
                Material currency = plugin.getConfigManager().getShopCurrency();
                int totalReward = achievement.reward;

                // Выдаём стаками по 64
                while (totalReward >= 64) {
                    TaxUtils.giveItemOrDrop(player, new ItemStack(currency, 64));
                    totalReward -= 64;
                }
                // Остаток
                if (totalReward > 0) {
                    TaxUtils.giveItemOrDrop(player, new ItemStack(currency, totalReward));
                }
            }

            // Уведомление в чат
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.sendMessage(Component.text("🏆 ")
                            .color(NamedTextColor.GOLD)
                            .append(Component.text(player.getName())
                                    .color(NamedTextColor.GREEN))
                            .append(Component.text(" получил достижение ")
                                    .color(NamedTextColor.GRAY))
                            .append(Component.text(achievement.name)
                                    .color(NamedTextColor.YELLOW)));
                }
            }
        }
    }

    public Achievement getAchievement(String id) {
        return achievements.stream()
                .filter(a -> a.id.equals(id))
                .findFirst()
                .orElse(null);
    }

    public Set<String> getUnlockedAchievements(UUID uuid) {
        return unlockedAchievements.getOrDefault(uuid, new HashSet<>());
    }

    public int getUnlockedCount(UUID uuid) {
        return getUnlockedAchievements(uuid).size();
    }

    public int getTotalAchievements() {
        return achievements.size();
    }

    public List<Achievement> getAllAchievements() {
        return achievements;
    }

    public static class Achievement {
        public final String id;
        public final String name;
        public final String description;
        public final int reward;

        public Achievement(String id, String name, String description, int reward) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.reward = reward;
        }
    }
}
