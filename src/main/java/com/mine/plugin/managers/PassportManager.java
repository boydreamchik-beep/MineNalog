package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PassportManager {

    private final MinePlugin plugin;
    private final File passportFile;
    private final Map<UUID, PassportData> passports = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;
    private BukkitTask autoSaveTask;

    public PassportManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.passportFile = new File(plugin.getDataFolder(), "passports.yml");
    }

    public void load() {
        passportFile.getParentFile().mkdirs();
        if (!passportFile.exists())
            try { passportFile.createNewFile(); } catch (IOException ignored) {}

        var cfg = YamlConfiguration.loadConfiguration(passportFile);
        passports.clear();
        if (cfg.contains("passports")) {
            var section = cfg.getConfigurationSection("passports");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        var s = section.getConfigurationSection(uuidStr);
                        if (s == null) continue;
                        PassportData d = new PassportData();
                        d.lastName = s.getString("last_name", "");
                        d.firstName = s.getString("first_name", "");
                        d.middleName = s.getString("middle_name", "");
                        d.birthDate = s.getString("birth_date", "");
                        d.birthPlace = s.getString("birth_place",
                                plugin.getConfigManager().getPassportBirthPlace());
                        d.gender = s.getString("gender", "");
                        d.residenceStatus = s.getString("residence_status", "В стране");
                        d.searchStatus = s.getString("search_status", "Нет");
                        d.residence = s.getString("residence", "Не указано");
                        d.playerName = s.getString("player_name", "");
                        d.issueDate = s.getLong("issue_date", 0);
                        passports.put(uuid, d);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Битый паспорт: " + uuidStr);
                    }
                }
            }
        }
    }

    public boolean issuePassport(UUID uuid, PassportData data) {
        if (passports.containsKey(uuid)) return false;
        passports.put(uuid, data);
        dirty = true;
        return true;
    }

    public boolean hasPassport(UUID uuid) { return passports.containsKey(uuid); }
    public PassportData getPassport(UUID uuid) { return passports.get(uuid); }

    public void updateResidenceStatus(UUID uuid, String status) {
        PassportData d = passports.get(uuid);
        if (d != null) { d.residenceStatus = status; dirty = true; }
    }

    public void updateResidence(UUID uuid, String residence) {
        PassportData d = passports.get(uuid);
        if (d != null) { d.residence = residence; dirty = true; }
    }

    public void startAutoSave() {
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) saveSync();
        }, 2400L, 2400L);
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) autoSaveTask.cancel();
    }

    public void saveSync() {
        Map<UUID, PassportData> snapshot = new HashMap<>(passports);
        dirty = false;
        var cfg = new YamlConfiguration();
        for (var e : snapshot.entrySet()) {
            String path = "passports." + e.getKey();
            PassportData d = e.getValue();
            cfg.set(path+".last_name", d.lastName);
            cfg.set(path+".first_name", d.firstName);
            cfg.set(path+".middle_name", d.middleName);
            cfg.set(path+".birth_date", d.birthDate);
            cfg.set(path+".birth_place", d.birthPlace);
            cfg.set(path+".gender", d.gender);
            cfg.set(path+".residence_status", d.residenceStatus);
            cfg.set(path+".search_status", d.searchStatus);
            cfg.set(path+".residence", d.residence);
            cfg.set(path+".player_name", d.playerName);
            cfg.set(path+".issue_date", d.issueDate);
        }
        try { cfg.save(passportFile); } catch (IOException e) {
            plugin.getLogger().severe("Ошибка passports.yml: "+e.getMessage());
        }
    }

    public static class PassportData {
        public String lastName="", firstName="", middleName="";
        public String birthDate="", birthPlace="", gender="";
        public String residenceStatus="В стране", searchStatus="Нет", residence="Не указано";
        public String playerName="";
        public long issueDate=0;
        public String getFullName() { return lastName+" "+firstName+" "+middleName; }
    }
}
