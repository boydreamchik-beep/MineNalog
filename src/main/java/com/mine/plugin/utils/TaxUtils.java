package com.mine.plugin.utils;

import org.bukkit.Material;

import java.util.Set;

public final class TaxUtils {

    private TaxUtils() {
        // Утилитный класс
    }

    /**
     * Блоки которые игрок оставляет себе как есть (не конвертируются).
     */
    public static final Set<Material> ALLOWED_MATERIALS = Set.of(
            Material.COBBLESTONE,
            Material.DIORITE,
            Material.ANDESITE
    );

    /**
     * Ценные руды — полностью уходят в казну города.
     */
    public static final Set<Material> TREASURY_ORES = Set.of(
            Material.IRON_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.LAPIS_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.COAL_ORE,
            Material.DEEPSLATE_COAL_ORE,
            Material.REDSTONE_ORE,
            Material.DEEPSLATE_REDSTONE_ORE,
            Material.COPPER_ORE,
            Material.DEEPSLATE_COPPER_ORE
    );

    public static boolean isTreasuryOre(Material material) {
        return TREASURY_ORES.contains(material);
    }

    public static boolean isAllowedMaterial(Material material) {
        return ALLOWED_MATERIALS.contains(material);
    }

    /**
     * Возвращает русское название материала для сообщений.
     */
    public static String getRussianName(Material material) {
        return switch (material) {
            case COBBLESTONE -> "Булыжник";
            case STONE -> "Камень";
            case DIORITE -> "Диорит";
            case ANDESITE -> "Андезит";
            case GRANITE -> "Гранит";
            case IRON_ORE -> "Железная руда";
            case DEEPSLATE_IRON_ORE -> "Глубинная железная руда";
            case GOLD_ORE -> "Золотая руда";
            case DEEPSLATE_GOLD_ORE -> "Глубинная золотая руда";
            case LAPIS_ORE -> "Лазуритовая руда";
            case DEEPSLATE_LAPIS_ORE -> "Глубинная лазуритовая руда";
            case DIAMOND_ORE -> "Алмазная руда";
            case DEEPSLATE_DIAMOND_ORE -> "Глубинная алмазная руда";
            case EMERALD_ORE -> "Изумрудная руда";
            case DEEPSLATE_EMERALD_ORE -> "Глубинная изумрудная руда";
            case COAL_ORE -> "Угольная руда";
            case DEEPSLATE_COAL_ORE -> "Глубинная угольная руда";
            case REDSTONE_ORE -> "Редстоуновая руда";
            case DEEPSLATE_REDSTONE_ORE -> "Глубинная редстоуновая руда";
            case COPPER_ORE -> "Медная руда";
            case DEEPSLATE_COPPER_ORE -> "Глубинная медная руда";
            case DIRT -> "Земля";
            case GRAVEL -> "Гравий";
            case SAND -> "Песок";
            case TUFF -> "Туф";
            default -> material.name();
        };
    }
}
