package com.mine.plugin.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Утилиты для налоговой системы и общих.helper функций.
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

    // =====================================================
    // ЗВУКОВЫЕ ЭФФЕКТЫ
    // =====================================================

    /**
     * Звук успеха (покупка, продажа, получение)
     */
    public static void playSuccessSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    /**
     * Звук ошибки (не хватает денег, отказ)
     */
    public static void playErrorSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    /**
     * Звук уведомления (налог, напоминание)
     */
    public static void playNotificationSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
    }

    /**
     * Звук телепортации
     */
    public static void playTeleportSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    // =====================================================
    // ФОРМАТИРОВАНИЕ СООБЩЕНИЙ
    // =====================================================

    /**
     * Создать красивое сообщение с заголовком
     */
    public static Component createTitleMessage(String title, NamedTextColor color) {
        return Component.text("╔══════════════════════════════╗").color(color)
                .appendNewline()
                .append(Component.text("║  " + title).color(color).decoration(TextDecoration.BOLD, true))
                .appendNewline()
                .append(Component.text("╚══════════════════════════════╝").color(color));
    }

    /**
     * Создать разделитель
     */
    public static Component createSeparator(NamedTextColor color) {
        return Component.text("══════════════════════════════").color(color);
    }
}
