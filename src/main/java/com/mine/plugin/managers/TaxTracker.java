package com.mine.plugin.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Счётчик блоков для правильного расчёта налога 20%.
 * Каждый 5-й добытый блок уходит в казну как налог.
 * Это решает проблему: ceil(1 * 0.2) = 1, то есть при
 * поблочном расчёте игрок ничего бы не получал.
 */
public class TaxTracker {

    // UUID игрока -> количество добытых блоков (обнуляется каждые 5)
    private final Map<UUID, Integer> blockCounter = new HashMap<>();

    /**
     * Увеличивает счётчик и возвращает true, если этот блок — налоговый (каждый 5-й).
     */
    public boolean incrementAndCheckTax(UUID playerId) {
        int current = blockCounter.getOrDefault(playerId, 0) + 1;

        if (current >= 5) {
            blockCounter.put(playerId, 0);
            return true; // Этот блок — налог
        }

        blockCounter.put(playerId, current);
        return false; // Этот блок — игроку
    }

    /**
     * Получить текущий счётчик игрока
     */
    public int getCount(UUID playerId) {
        return blockCounter.getOrDefault(playerId, 0);
    }

    /**
     * Сбросить счётчик игрока
     */
    public void reset(UUID playerId) {
        blockCounter.remove(playerId);
    }
}
