package com.mine.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Маркерный InventoryHolder для идентификации наших GUI.
 * 
 * ИЗМЕНЕНИЕ: Добавлен тип ALREADY_IN_MINE для окна
 * "вы уже в шахте"
 */
public class MineInventoryHolder implements InventoryHolder {

    public enum GUIType {
        MINE_LEVEL_SELECT,     // Выбор уровня шахты
        KAZNA_VIEW,            // Просмотр казны
        ALREADY_IN_MINE        // ➕ НОВЫЙ: Окно "вы уже в шахте"
    }

    private final GUIType type;

    public MineInventoryHolder(GUIType type) {
        this.type = type;
    }

    public GUIType getType() {
        return type;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}
