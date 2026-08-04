package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PassportManager {

    private final MinePlugin plugin;
    private final File passportFile;
    private FileConfiguration passportConfig;
    private final Map<UUID, PassportData> passports = new HashMap<>();

    public PassportManager(MinePlugin plugin) {
        this.plugin = plugin;
        this.passportFile = new File(plugin.getDataFolder(), "passports.yml");
    }

    public void load() {
        if (!passportFile.exists()) {
            try { passportFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        passportConfig = YamlConfiguration.loadConfiguration(passportFile);
        passports.clear();

        if (passportConfig.contains("passports")) {
            var section = passportConfig.getConfigurationSection("passports");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        var s = section.getConfigurationSection(uuidStr);
                        if (s == null) continue;

                        PassportData data = new PassportData();
                        data.lastName = s.getString("last_name", "");
                        data.firstName = s.getString("first_name", "");
                        data.middleName = s.getString("middle_name", "");
                        data.birthDate = s.getString("birth_date", "");
                        data.birthPlace = s.getString("birth_place", "");
                        data.gender = s.getString("gender", "");
                        data.residenceStatus = s.getString("residence_status", "В стране");
                        data.searchStatus = s.getString("search_status", "Нет");
                        data.residence = s.getString("residence", "Не указано");
                        data.playerName = s.getString("player_name", "");
                        data.issueDate = s.getLong("issue_date", 0);

                        passports.put(uuid, data);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public void save() {
        passportConfig = new YamlConfiguration();
        for (var entry : passports.entrySet()) {
            String path = "passports." + entry.getKey().toString();
            PassportData d = entry.getValue();
            passportConfig.set(path + ".last_name", d.lastName);
            passportConfig.set(path + ".first_name", d.firstName);
            passportConfig.set(path + ".middle_name", d.middleName);
            passportConfig.set(path + ".birth_date", d.birthDate);
            passportConfig.set(path + ".birth_place", d.birthPlace);
            passportConfig.set(path + ".gender", d.gender);
            passportConfig.set(path + ".residence_status", d.residenceStatus);
            passportConfig.set(path + ".search_status", d.searchStatus);
            passportConfig.set(path + ".residence", d.residence);
            passportConfig.set(path + ".player_name", d.playerName);
            passportConfig.set(path + ".issue_date", d.issueDate);
        }
        try { passportConfig.save(passportFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public boolean issuePassport(UUID uuid, PassportData data) {
        if (passports.containsKey(uuid)) return false;
        passports.put(uuid, data);
        save();
        return true;
    }

    public boolean hasPassport(UUID uuid) { return passports.containsKey(uuid); }
    public PassportData getPassport(UUID uuid) { return passports.get(uuid); }

    public void updateResidenceStatus(UUID uuid, String status) {
        PassportData data = passports.get(uuid);
        if (data != null) {
            data.residenceStatus = status;
            save();
        }
    }

    public void updateResidence(UUID uuid, String residence) {
        PassportData data = passports.get(uuid);
        if (data != null) {
            data.residence = residence;
            save();
        }
    }

    public static class PassportData {
        public String lastName = "";
        public String firstName = "";
        public String middleName = "";
        public String birthDate = "";
        public String birthPlace = "";
        public String gender = "";
        public String residenceStatus = "В стране";
        public String searchStatus = "Нет";
        public String residence = "Не указано";
        public String playerName = "";
        public long issueDate = 0;

        public String getFullName() {
            return lastName + " " + firstName + " " + middleName;
        }
    }
}
