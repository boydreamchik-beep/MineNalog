package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.utils.TaxUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Ежедневный бонус за вход в шахту.
 * - Награда растёт с каждым днём подряд (streak)
 * - День 1: 32, День 2: 64, День 3: 96... максимум 512
 * - При пропуске дня серия сбрасывается
 */
public class DailyBonusManager {

    private final MinePlugin plugin;
    private final File bonusFile;
    private final Map<UUID, BonusData> bonusData = new HashMap<>();
    private BukkitTask autoSaveTask;
    private volatile boolean dirty = false;

    // Награды по дням серии (streak)
    private static final int[] STREAK_REWARDS = {32, 64, 96, 128, 192, 256, 384, 512};
    private static final int MAX_STREAK_INDEX = STREAK_REWARDS.length - 1;

    public DailyBonusManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.bonusFile = new File(plugin.getDataFolder(), "daily_bonus.yml");
    }

    public void load() {
        if (!bonusFile.getParentFile().exists()) {
            bonusFile.getParentFile().mkdirs();
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(bonusFile);

        if (cfg.contains("bonus")) {
            var section = cfg.getConfigurationSection("bonus");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        long lastClaim = section.getLong(uuidStr + ".last_claim", 0);
                        int streak = section.getInt(uuidStr + ".streak", 0);
                        bonusData.put(uuid, new BonusData(lastClaim, streak));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Битая запись daily_bonus: " + uuidStr);
                    }
                }
            }
        }

        startAutoSave();
    }

    public void startAutoSave() {
        autoSaveTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 2400L, 2400L);
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) autoSaveTask.cancel();
    }

    public void save() {
        dirty = false;
        YamlConfiguration cfg = new YamlConfiguration();

        for (var entry : bonusData.entrySet()) {
            String path = "bonus." + entry.getKey();
            cfg.set(path + ".last_claim", entry.getValue().lastClaim);
            cfg.set(path + ".streak", entry.getValue().streak);
        }

        try {
            cfg.save(bonusFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить daily_bonus.yml: " + e.getMessage());
        }
    }

    /**
     * Попытаться выдать ежедневный бонус при входе в шахту.
     * @return true если бонус выдан, false если уже получен сегодня
     */
    public boolean tryClaimBonus(Player player) {
        UUID uuid = player.getUniqueId();
        BonusData data = bonusData.computeIfAbsent(uuid, k -> new BonusData(0, 0));

        long now = System.currentTimeMillis();
        long today = getDayStart(now);
        long lastClaimDay = getDayStart(data.lastClaim);

        // Уже получал сегодня
        if (data.lastClaim > 0 && today == lastClaimDay) {
            return false;
        }

        // Определяем streak
        long yesterday = today - TimeUnit.DAYS.toMillis(1);
        if (lastClaimDay == yesterday) {
            // Продолжаем серию
            data.streak = Math.min(data.streak + 1, MAX_STREAK_INDEX);
        } else if (lastClaimDay < yesterday) {
            // Серия прервана
            data.streak = 0;
        }
        // Если lastClaim == 0 — первый раз, streak = 0

        int reward = STREAK_REWARDS[data.streak];
        data.lastClaim = now;
        dirty = true;

        // Выдаём награду
        Material currency = plugin.getConfigManager().getShopCurrency();
        int totalReward = reward;
        while (totalReward >= 64) {
            TaxUtils.giveItemOrDrop(player, new org.bukkit.inventory.ItemStack(currency, 64));
            totalReward -= 64;
        }
        if (totalReward > 0) {
            TaxUtils.giveItemOrDrop(player, new org.bukkit.inventory.ItemStack(currency, totalReward));
        }

        // Звук
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        // Сообщение
        int streakDay = data.streak + 1;
        int nextReward = data.streak < MAX_STREAK_INDEX ? STREAK_REWARDS[data.streak + 1] : reward;

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("╔══════════════════════════════╗")
                .color(NamedTextColor.DARK_GREEN));
        player.sendMessage(Component.text("║  🎁 ЕЖЕДНЕВНЫЙ БОНУС!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("║")
                .color(NamedTextColor.DARK_GREEN));
        player.sendMessage(Component.text("║  Получено: " + reward + " булыжника")
                .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("║  Серия: " + streakDay + " день 🔥")
                .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  Завтра: " + nextReward + " булыжника")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("╚══════════════════════════════╝")
                .color(NamedTextColor.DARK_GREEN));
        player.sendMessage(Component.empty());

        return true;
    }

    /**
     * Получить информацию о бонусе игрока
     */
    public BonusData getBonusData(UUID uuid) {
        return bonusData.getOrDefault(uuid, new BonusData(0, 0));
    }

    /**
     * Получить начало дня (полночь) в миллисекундах
     */
    private long getDayStart(long timeMillis) {
        return (timeMillis / TimeUnit.DAYS.toMillis(1)) * TimeUnit.DAYS.toMillis(1);
    }

    public static class BonusData {
        public long lastClaim;
        public int streak;

        public BonusData(long lastClaim, int streak) {
            this.lastClaim = lastClaim;
            this.streak = streak;
        }
    }
}
