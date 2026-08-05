package com.mine.plugin.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Счётчик блоков для правильного расчёта налога в шахте.
 * Поддерживает разные проценты для разных уровней шахты.
 * 
 * Пример: 20% = каждый 5-й блок, 15% = каждый 7-й блок
 * Это решает проблему: ceil(1 * 0.2) = 1, то есть при
 * поблочном расчёте игрок ничего бы не получал.
 */
public class TaxTracker {

    // UUID игрока -> количество добытых блоков с момента последнего налога
    private final Map<UUID, Integer> blockCounter = new ConcurrentHashMap<>();

    /**
     * Увеличивает счётчик и возвращает true, если этот блок — налоговый.
     * @param playerId UUID игрока
     * @param taxEvery Каждый N-й блок уходит в налог (5 для 20%, 7 для 15%)
     * @return true если блок облагается налогом
     */
    public boolean incrementAndCheckTax(UUID playerId, int taxEvery) {
        if (taxEvery <= 0) taxEvery = 5; // защита от деления на 0
        
        int current = blockCounter.getOrDefault(playerId, 0) + 1;

        if (current >= taxEvery) {
            blockCounter.put(playerId, 0);
            return true; // Этот блок — налог
        }

        blockCounter.put(playerId, current);
        return false; // Этот блок — игроку
    }

    /**
     * Устаревший метод для обратной совместимости (всегда 5 блоков)
     * @deprecated Используйте incrementAndCheckTax(uuid, taxEvery)
     */
    @Deprecated
    public boolean incrementAndCheckTax(UUID playerId) {
        return incrementAndCheckTax(playerId, 5);
    }

    /**
     * Получить текущий счётчик игрока (сколько блоков добыто с последнего налога)
     */
    public int getCount(UUID playerId) {
        return blockCounter.getOrDefault(playerId, 0);
    }

    /**
     * Сбросить счётчик игрока (при выходе из шахты)
     */
    public void reset(UUID playerId) {
        blockCounter.remove(playerId);
    }

    /**
     * Очистить все счётчики (при рестарте сервера)
     */
    public void resetAll() {
        blockCounter.clear();
    }
}
