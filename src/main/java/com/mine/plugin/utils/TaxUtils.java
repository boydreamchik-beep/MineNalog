package com.mine.plugin.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

public final class TaxUtils {

    private TaxUtils() {}

    // =============================================
    // ИМЕНА МАТЕРИАЛОВ НА РУССКОМ
    // =============================================

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
            case OAK_LOG -> "Дубовое бревно";
            case PORKCHOP -> "Свинина";
            case COAL -> "Уголь";
            case RAW_IRON -> "Сырое железо";
            case DIAMOND -> "Алмаз";
            case DIRT -> "Земля";
            case GRAVEL -> "Гравий";
            case SAND -> "Песок";
            case TUFF -> "Туф";
            default -> prettify(material.name());
        };
    }

    private static String prettify(String name) {
        String text = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // =============================================
    // ОБЩИЕ ОПЕРАЦИИ С ИНВЕНТАРЁМ (DRY)
    // =============================================

    /**
     * Посчитать количество материала в инвентаре игрока.
     * НЕ включает сундуки — для быстрых проверок (GUI).
     */
    public static int countInInventory(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material && !isMineCompass(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Списать материал из инвентаря игрока. Защищает компас.
     *
     * @return сколько не удалось списать
     */
    public static int removeFromInventory(Player player, Material material, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() != material || isMineCompass(item)) continue;

            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }
        return remaining;
    }

    /**
     * Проверка компаса шахты (чтобы не было импорта везде).
     */
    public static boolean isMineCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        var meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        var name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName());
        return name.contains("Выход из шахты");
    }

    /**
     * Дать предмет игроку с дропом излишков на землю.
     */
    public static void giveItemOrDrop(Player player, ItemStack toGive) {
        var overflow = player.getInventory().addItem(toGive);
        for (ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }
}
