package com.mine.plugin.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Утилиты для налоговой системы.
 * Все методы статические, класс не создаётся.
 */
public final class TaxUtils {

    private TaxUtils() {}

    /**
     * Подсчитать количество материала в инвентаре игрока.
     */
    public static int countInInventory(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Удалить material из инвентаря игрока.
     * @return остаток, который не удалось удалить (0 если всё удалено)
     */
    public static int removeFromInventory(Player player, Material material, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != material) continue;

            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
            if (remaining <= 0) break;
        }
        return remaining;
    }

    /**
     * Выдать предмет игроку; если инвентарь переполнен — выбросить на землю.
     */
    public static void giveItemOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /**
     * Получить русское название материала для отображения игроку
     */
    public static String getRussianName(Material material) {
        if (material == null) return "Неизвестно";
        
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
            case OAK_LOG -> "Дубовое бревно";
            case PORKCHOP -> "Свинина";
            case COAL -> "Уголь";
            case RAW_IRON -> "Сырое железо";
            case DIAMOND -> "Алмаз";
            case DIRT -> "Земля";
            case GRAVEL -> "Гравий";
            case SAND -> "Песок";
            case TUFF -> "Туф";
            case NETHER_GOLD_ORE -> "Золотая руда Нижнего мира";
            case NETHER_QUARTZ_ORE -> "Кварцевая руда";
            case ANCIENT_DEBRIS -> "Древний обломок";
            case RAW_COPPER -> "Сырая медь";
            case RAW_GOLD -> "Сырое золото";
            case EMERALD_BLOCK -> "Изумрудный блок";
            case DIAMOND_BLOCK -> "Алмазный блок";
            case GOLD_BLOCK -> "Золотой блок";
            case IRON_BLOCK -> "Железный блок";
            default -> material.name();
        };
    }

    /**
     * Получить русское название материала с правильным падежом для сообщений
     */
    public static String getRussianNameGenitive(Material material, int amount) {
        String base = getRussianName(material);
        
        // Простая логика склонений (можно расширить)
        if (base.endsWith("а") || base.endsWith("я")) {
            return base; // уже в нужном падеже для большинства
        }
        return base;
    }
}
