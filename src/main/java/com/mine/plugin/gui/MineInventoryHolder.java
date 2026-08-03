package com.mine.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * ИЗМЕНЕНИЕ: Добавлены типы SHOP и KAZNA_PAGED
 */
public class MineInventoryHolder implements InventoryHolder {

    public enum GUIType {
        MINE_LEVEL_SELECT,
        KAZNA_VIEW,
        ALREADY_IN_MINE,
        SHOP,              // ➕ НОВЫЙ
        KAZNA_PAGED        // ➕ НОВЫЙ: казна с пагинацией
    }

    private final GUIType type;
    private int page = 0;  // ➕ НОВЫЙ: номер страницы для казны

    public MineInventoryHolder(GUIType type) {
        this.type = type;
    }

    public MineInventoryHolder(GUIType type, int page) {
        this.type = type;
        this.page = page;
    }

    public GUIType getType() {
        return type;
    }

    public int getPage() {
        return page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}
