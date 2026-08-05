package com.mine.plugin.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Счётчик блоков для налога в шахте.
 * Персональный для каждого игрока: каждому N-й блок → казна.
 */
public class TaxTracker {

    private final Map<UUID, Integer> blockCounter = new HashMap<>();

    /**
     * Увеличивает счётчик и проверяет, является ли текущий блок налоговым.
     *
     * @param playerId   UUID игрока
     * @param taxEvery   каждый какой блок удерживается (например 5 для 20%)
     *                   Integer.MAX_VALUE означает налог отключён
     * @return true если этот блок нужно отправить в казну
     */
    public boolean incrementAndCheckTax(UUID playerId, int taxEvery) {
        if (taxEvery <= 0 || taxEvery == Integer.MAX_VALUE) return false;

        int current = blockCounter.getOrDefault(playerId, 0) + 1;

        if (current >= taxEvery) {
            blockCounter.put(playerId, 0);
            return true;
        }

        blockCounter.put(playerId, current);
        return false;
    }

    public int getCount(UUID playerId) {
        return blockCounter.getOrDefault(playerId, 0);
    }

    public void reset(UUID playerId) {
        blockCounter.remove(playerId);
    }

    /** Полная очистка при выключении. */
    public void clear() {
        blockCounter.clear();
    }
}
