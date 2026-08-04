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

public class CreditManager {

    private final MinePlugin plugin;
    private final File creditFile;
    private FileConfiguration creditConfig;
    private final Map<UUID, CreditData> credits = new HashMap<>();
    private BukkitTask reminderTask;

    public CreditManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.creditFile = new File(plugin.getDataFolder(), "credits.yml");
    }

    public void load() {
        if (!creditFile.exists()) {
            try { creditFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
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
                            credits.put(uuid, new CreditData(amount, takenTime, lastReminder, totalPaid));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public void save() {
        creditConfig = new YamlConfiguration();
        for (var entry : credits.entrySet()) {
            String path = "credits." + entry.getKey().toString();
            CreditData d = entry.getValue();
            creditConfig.set(path + ".amount", d.amount);
            creditConfig.set(path + ".taken_time", d.takenTime);
            creditConfig.set(path + ".last_reminder", d.lastReminder);
            creditConfig.set(path + ".total_paid", d.totalPaid);
        }
        try { creditConfig.save(creditFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public void startReminders() {
        reminderTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            ConfigManager cfg = plugin.getConfigManager();
            double rate = cfg.getCreditInterestRate();
            long intervalMs = cfg.getCreditInterestDays() * 24L * 60 * 60 * 1000;

            for (var entry : credits.entrySet()) {
                CreditData data = entry.getValue();
                if ((now - data.lastReminder) >= intervalMs) {
                    int interest = (int) Math.ceil(data.amount * rate);
                    data.amount += interest;
                    data.lastReminder = now;

                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.sendMessage(Component.text("[Кредит] Начислены проценты: +"
                                        + interest + ". Долг: " + data.amount)
                                .color(NamedTextColor.RED));
                    }
                    save();
                }
            }
        }, 72000L, 72000L);
    }

    public void stopReminders() {
        if (reminderTask != null) reminderTask.cancel();
    }

    public TakeResult takeCredit(UUID uuid, int amount) {
        if (amount <= 0) return TakeResult.INVALID_AMOUNT;
        int maxAmount = plugin.getConfigManager().getCreditMaxAmount();
        CreditData existing = credits.get(uuid);
        int currentDebt = existing != null ? existing.amount : 0;
        if (currentDebt + amount > maxAmount) return TakeResult.EXCEEDS_LIMIT;

        if (existing != null) {
            existing.amount += amount;
        } else {
            long now = System.currentTimeMillis();
            credits.put(uuid, new CreditData(amount, now, now, 0));
        }
        save();
        return TakeResult.SUCCESS;
    }

    public PayResult payCredit(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        CreditData data = credits.get(uuid);
        if (data == null || data.amount <= 0) return PayResult.NO_CREDIT;
        if (amount <= 0) return PayResult.INVALID_AMOUNT;

        int actualPay = Math.min(amount, data.amount);
        Material currency = plugin.getConfigManager().getShopCurrency();
        int playerAmount = countMaterial(player, currency);
        if (playerAmount < actualPay) return PayResult.NOT_ENOUGH;

        removeMaterial(player, currency, actualPay);
        data.amount -= actualPay;
        data.totalPaid += actualPay;
        if (data.amount <= 0) credits.remove(uuid);
        save();
        return PayResult.SUCCESS;
    }

    public void giveCobblestone(Player player, int amount) {
        Material currency = plugin.getConfigManager().getShopCurrency();
        int full = amount / 64;
        int rem = amount % 64;
        for (int i = 0; i < full; i++) {
            var overflow = player.getInventory().addItem(new ItemStack(currency, 64));
            for (ItemStack item : overflow.values())
                player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        if (rem > 0) {
            var overflow = player.getInventory().addItem(new ItemStack(currency, rem));
            for (ItemStack item : overflow.values())
                player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    public CreditData getCreditData(UUID uuid) { return credits.get(uuid); }
    public boolean hasCredit(UUID uuid) {
        CreditData d = credits.get(uuid);
        return d != null && d.amount > 0;
    }

    private int countMaterial(Player player, Material mat) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents())
            if (item != null && item.getType() == mat) count += item.getAmount();
        return count;
    }

    private void removeMaterial(Player player, Material mat, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != mat) continue;
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

    public static class CreditData {
        public int amount;
        public long takenTime;
        public long lastReminder;
        public int totalPaid;
        public CreditData(int amount, long takenTime, long lastReminder, int totalPaid) {
            this.amount = amount; this.takenTime = takenTime;
            this.lastReminder = lastReminder; this.totalPaid = totalPaid;
        }
    }

    public enum TakeResult { SUCCESS, EXCEEDS_LIMIT, INVALID_AMOUNT }
    public enum PayResult { SUCCESS, NO_CREDIT, INVALID_AMOUNT, NOT_ENOUGH }
}
