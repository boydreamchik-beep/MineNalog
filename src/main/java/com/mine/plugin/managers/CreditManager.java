package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.utils.TaxUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
    private YamlConfiguration creditConfig;
    private final Map<UUID, CreditData> credits = new HashMap<>(); // только main thread
    private volatile boolean dirty = false;
    private BukkitTask reminderTask;

    public CreditManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.creditFile = new File(plugin.getDataFolder(), "credits.yml");
    }

    public void load() {
        creditFile.getParentFile().mkdirs();
        if (!creditFile.exists()) {
            try { creditFile.createNewFile(); } catch (IOException ignored) {}
        }
        creditConfig = YamlConfiguration.loadConfiguration(creditFile);
        credits.clear();
        if (creditConfig.contains("credits")) {
            var section = creditConfig.getConfigurationSection("credits");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        int amt = section.getInt(uuidStr + ".amount", 0);
                        if (amt > 0) credits.put(uuid, new CreditData(
                                amt,
                                section.getLong(uuidStr + ".taken_time", System.currentTimeMillis()),
                                section.getLong(uuidStr + ".last_reminder", System.currentTimeMillis()),
                                section.getInt(uuidStr + ".total_paid", 0)));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Битая запись кредита: " + uuidStr);
                    }
                }
            }
        }
    }

    public void startReminders() {
        long intervalMs = (long) plugin.getConfigManager().getCreditInterestDays() * 24 * 60 * 60 * 1000;
        reminderTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double rate = plugin.getConfigManager().getCreditInterestRate();
            boolean changed = false;

            for (var entry : credits.entrySet()) {
                CreditData data = entry.getValue();
                while ((System.currentTimeMillis() - data.lastReminder) >= intervalMs) {
                    int interest = (int) Math.ceil(data.amount * rate);
                    data.amount += interest;
                    data.lastReminder += intervalMs;
                    changed = true;

                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null && p.isOnline()) {
                        p.sendMessage(Component.text("[Кредит] Проценты: +" + interest
                                        + ". Долг: " + data.amount).color(NamedTextColor.RED));
                    }
                }
            }
            if (changed) { dirty = true; saveSync(); }
        }, 1200L, 72000L); // первый через минуту, потом час
    }

    public void stopReminders() {
        if (reminderTask != null) reminderTask.cancel();
    }

    public TakeResult takeCredit(UUID uuid, int amount) {
        if (amount <= 0) return TakeResult.INVALID_AMOUNT;
        int maxAmount = plugin.getConfigManager().getCreditMaxAmount();
        int currentDebt = credits.containsKey(uuid) ? credits.get(uuid).amount : 0;
        if (currentDebt + amount > maxAmount) return TakeResult.EXCEEDS_LIMIT;

        long now = System.currentTimeMillis();
        if (credits.containsKey(uuid)) {
            credits.get(uuid).amount += amount;
        } else {
            credits.put(uuid, new CreditData(amount, now, now, 0));
        }
        dirty = true;
        return TakeResult.SUCCESS;
    }

    public PayResult payCredit(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        CreditData data = credits.get(uuid);
        if (data == null || data.amount <= 0) return PayResult.NO_CREDIT;
        if (amount <= 0) return PayResult.INVALID_AMOUNT;

        int actualPay = Math.min(amount, data.amount);
        Material currency = plugin.getConfigManager().getShopCurrency();
        int playerTotal = TaxUtils.countInInventory(player, currency);
        if (playerTotal < actualPay) return PayResult.NOT_ENOUGH;

        TaxUtils.removeFromInventory(player, currency, actualPay);
        data.amount -= actualPay;
        data.totalPaid += actualPay;
        if (data.amount <= 0) credits.remove(uuid);

        dirty = true;
        return PayResult.SUCCESS;
    }

    public void giveCobblestone(Player player, int amount) {
        Material currency = plugin.getConfigManager().getShopCurrency();
        int full = amount / 64;
        int rem = amount % 64;
        for (int i = 0; i < full; i++)
            TaxUtils.giveItemOrDrop(player, new ItemStack(currency, 64));
        if (rem > 0) TaxUtils.giveItemOrDrop(player, new ItemStack(currency, rem));
    }

    public CreditData getCreditData(UUID uuid) { return credits.get(uuid); }
    public boolean hasCredit(UUID uuid) { return credits.containsKey(uuid) && credits.get(uuid).amount > 0; }

    /** Для onDisable. */
    public void saveSync() {
        var snapshot = new HashMap<>(credits);
        dirty = false;
        var cfg = new YamlConfiguration();
        for (var e : snapshot.entrySet()) {
            String path = "credits." + e.getKey();
            var d = e.getValue();
            cfg.set(path + ".amount", d.amount);
            cfg.set(path + ".taken_time", d.takenTime);
            cfg.set(path + ".last_reminder", d.lastReminder);
            cfg.set(path + ".total_paid", d.totalPaid);
        }
        try { cfg.save(creditFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public static class CreditData {
        public int amount; public long takenTime, lastReminder, totalPaid;
        public CreditData(int a, long tt, long lr, int tp) {
            amount=a; takenTime=tt; lastReminder=lr; totalPaid=tp;
        }
    }

    public enum TakeResult { SUCCESS, EXCEEDS_LIMIT, INVALID_AMOUNT }
    public enum PayResult { SUCCESS, NO_CREDIT, INVALID_AMOUNT, NOT_ENOUGH }
}
