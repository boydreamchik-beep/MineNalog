package com.mine.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Маркерный InventoryHolder для идентификации наших GUI.
 * Это надёжнее чем сравнение заголовков через строки.
 */
public class MineInventoryHolder implements InventoryHolder {

    public enum GUIType {
        MINE_LEVEL_SELECT,
        KAZNA_VIEW
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
        // Не используется, но обязателен для интерфейса
        return null;
    }
}
