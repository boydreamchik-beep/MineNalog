package com.mine.plugin.listeners;

import com.mine.plugin.MinePlugin;
import com.mine.plugin.commands.PropertyCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Настоящий NPC (Villager) на координатах property.npc.
 * 
 * - Спавнится при старте плагина
 * - Неуязвим (нельзя убить)
 * - Не двигается (AI выключен)
 * - При ПКМ по NPC открывается меню имущества
 * - Помечен PersistentData ключом для идентификации
 */
public class NPCListener implements Listener {

    private final MinePlugin plugin;
    private final PropertyCommand propertyCommand;
    private final NamespacedKey npcKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    // Ссылка на живого NPC
    private Villager propertyNPC;

    public NPCListener(MinePlugin plugin, PropertyCommand propertyCommand) {
        this.plugin = plugin;
        this.propertyCommand = propertyCommand;
        this.npcKey = new NamespacedKey(plugin, "property_npc");
    }

    /**
     * Заспавнить NPC (вызывается после загрузки плагина)
     */
    public void spawnNPC() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            double x = plugin.getConfig().getDouble("property.npc.x", -204.304);
            double y = plugin.getConfig().getDouble("property.npc.y", 66.0);
            double z = plugin.getConfig().getDouble("property.npc.z", -24.418);

            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world == null) {
                plugin.getLogger().warning("Не удалось спавнить NPC — мир не найден!");
                return;
            }

            Location npcLoc = new Location(world, x, y, z);

            // Загружаем чанк
            npcLoc.getChunk().load(true);

            // Удаляем всех старых NPC
            removeExistingNPCs(world);

            // Спавним нового
            Villager villager = (Villager) world.spawnEntity(npcLoc, EntityType.VILLAGER);

            // Настройки
            villager.customName(Component.text("Риелтор").color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true));
            villager.setCustomNameVisible(true);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setPersistent(true);
            villager.setProfession(Villager.Profession.LIBRARIAN);

            // Помечаем как наш NPC
            villager.getPersistentDataContainer().set(
                    npcKey, PersistentDataType.STRING, "property");

            propertyNPC = villager;

            plugin.getLogger().info("NPC 'Риелтор' заспавнен: " + x + ", " + y + ", " + z);
        }, 40L);
    }

    /**
     * Удалить всех наших NPC (при перезапуске плагина)
     */
    public void removeExistingNPCs(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Villager villager) {
                if (villager.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) {
                    villager.remove();
                }
            }
        }
    }

    /**
     * Удалить NPC при выключении плагина
     */
    public void despawnNPC() {
        if (propertyNPC != null && !propertyNPC.isDead()) {
            propertyNPC.remove();
            propertyNPC = null;
        }
        // На всякий случай удаляем всех
        for (World world : Bukkit.getWorlds()) {
            removeExistingNPCs(world);
        }
    }

    /**
     * Проверить NPC жив ли, если нет — заспавнить снова
     */
    public boolean isOurNPC(Entity entity) {
        if (!(entity instanceof Villager)) return false;
        return entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING);
    }

    /**
     * ПКМ по NPC — открыть меню
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!isOurNPC(event.getRightClicked())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        Long last = cooldowns.get(uuid);
        if (last != null && now - last < 1000) return;
        cooldowns.put(uuid, now);

        propertyCommand.openPropertyMenu(player);
    }

    /**
     * ПКМ по NPC (второй тип события — надёжнее)
     */
    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!isOurNPC(event.getRightClicked())) return;
        event.setCancelled(true);
    }

    /**
     * Защита NPC от повреждений
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (isOurNPC(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * При загрузке чанка — проверяем, есть ли NPC
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Проверяем что это чанк с NPC
        double npcX = plugin.getConfig().getDouble("property.npc.x", -204.304);
        double npcZ = plugin.getConfig().getDouble("property.npc.z", -24.418);
        int chunkX = (int) Math.floor(npcX / 16);
        int chunkZ = (int) Math.floor(npcZ / 16);

        if (event.getChunk().getX() == chunkX && event.getChunk().getZ() == chunkZ) {
            // Проверяем, есть ли NPC в мире
            boolean found = false;
            for (Entity entity : event.getChunk().getEntities()) {
                if (isOurNPC(entity)) {
                    found = true;
                    propertyNPC = (Villager) entity;
                    break;
                }
            }
            if (!found) {
                // Нет NPC — спавним
                spawnNPC();
            }
        }
    }
}
