package com.mine.plugin.managers;

import com.mine.plugin.MinePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер паспортов.
 * Паспорт можно получить ОДИН РАЗ.
 * Хранит ФИО игрока.
 * Данные в passports.yml
 */
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
            try {
                passportFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось создать passports.yml!");
                e.printStackTrace();
            }
        }

        passportConfig = YamlConfiguration.loadConfiguration(passportFile);
        passports.clear();

        if (passportConfig.contains("passports")) {
            var section = passportConfig.getConfigurationSection("passports");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        String lastName = section.getString(uuidStr + ".last_name", "");
                        String firstName = section.getString(uuidStr + ".first_name", "");
                        String middleName = section.getString(uuidStr + ".middle_name", "");
                        long issueDate = section.getLong(uuidStr + ".issue_date", 0);
                        String playerName = section.getString(uuidStr + ".player_name", "");

                        PassportData data = new PassportData(
                                lastName, firstName, middleName, issueDate, playerName);
                        passports.put(uuid, data);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Ошибка загрузки паспорта: " + uuidStr);
                    }
                }
            }
        }

        plugin.getLogger().info("Паспортов загружено: " + passports.size());
    }

    public void save() {
        passportConfig = new YamlConfiguration();

        for (Map.Entry<UUID, PassportData> entry : passports.entrySet()) {
            String path = "passports." + entry.getKey().toString();
            PassportData data = entry.getValue();
            passportConfig.set(path + ".last_name", data.lastName);
            passportConfig.set(path + ".first_name", data.firstName);
            passportConfig.set(path + ".middle_name", data.middleName);
            passportConfig.set(path + ".issue_date", data.issueDate);
            passportConfig.set(path + ".player_name", data.playerName);
        }

        try {
            passportConfig.save(passportFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить passports.yml!");
            e.printStackTrace();
        }
    }

    /**
     * Выдать паспорт (только один раз!)
     */
    public boolean issuePassport(UUID uuid, String playerName,
                                  String lastName, String firstName, String middleName) {
        if (passports.containsKey(uuid)) {
            return false; // Уже есть паспорт
        }

        PassportData data = new PassportData(
                lastName, firstName, middleName,
                System.currentTimeMillis(), playerName);
        passports.put(uuid, data);
        save();
        return true;
    }

    /**
     * Есть ли паспорт у игрока
     */
    public boolean hasPassport(UUID uuid) {
        return passports.containsKey(uuid);
    }

    /**
     * Получить данные паспорта
     */
    public PassportData getPassport(UUID uuid) {
        return passports.get(uuid);
    }

    // === Класс данных паспорта ===

    public static class PassportData {
        public final String lastName;
        public final String firstName;
        public final String middleName;
        public final long issueDate;
        public final String playerName;

        public PassportData(String lastName, String firstName, String middleName,
                            long issueDate, String playerName) {
            this.lastName = lastName;
            this.firstName = firstName;
            this.middleName = middleName;
            this.issueDate = issueDate;
            this.playerName = playerName;
        }

        public String getFullName() {
            return lastName + " " + firstName + " " + middleName;
        }
    }
}
