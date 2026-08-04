package com.mine.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class MineInventoryHolder implements InventoryHolder {

    public enum GUIType {
        MINE_LEVEL_SELECT,
        KAZNA_VIEW,
        ALREADY_IN_MINE,
        SHOP,
        KAZNA_PAGED,
        CREDIT,
        PASSPORT,
        PROPERTY_MENU,
        PROPERTY_PLOT_CONFIRM,
        INSTALLMENT_CONFIRM
    }

    private final GUIType type;
    private int page = 0;
    private String extraData = "";

    public MineInventoryHolder(GUIType type) {
        this.type = type;
    }

    public MineInventoryHolder(GUIType type, int page) {
        this.type = type;
        this.page = page;
    }

    public MineInventoryHolder(GUIType type, String extraData) {
        this.type = type;
        this.extraData = extraData;
    }

    public GUIType getType() { return type; }
    public int getPage() { return page; }
    public String getExtraData() { return extraData; }

    @Override
    public @NotNull Inventory getInventory() { return null; }
}
