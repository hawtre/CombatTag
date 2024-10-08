package net.minelink.ctplus;

import net.minelink.ctplus.event.NpcDespawnEvent;
import net.minelink.ctplus.event.NpcDespawnReason;
import net.minelink.ctplus.task.NpcDespawnTask;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NpcManager {

    private final CombatTagPlus plugin;

    private final Map<UUID, Npc> spawnedNpcs = new HashMap<>();

    private final Map<Npc, NpcDespawnTask> despawnTasks = new HashMap<>();

    NpcManager(CombatTagPlus plugin) {
        this.plugin = plugin;
    }

    public Npc spawn(Player player) {
        // Do nothing if player already has a NPC
        Npc npc = getSpawnedNpc(player.getUniqueId());
        if (npc != null) return null;

        // Spawn fake player entity
        npc = new Npc(plugin.getNpcPlayerHelper(), plugin.getNpcPlayerHelper().spawn(player));
        spawnedNpcs.put(player.getUniqueId(), npc);

        Player entity = npc.getEntity();

        entity.setCanPickupItems(false);
        entity.setNoDamageTicks(0);

        // Copy player data to fake player
        entity.setHealthScale(player.getHealthScale());
        entity.setMaxHealth(player.getMaxHealth());
        entity.setHealth(player.getHealth());
        entity.setTotalExperience(player.getTotalExperience());
        entity.setFoodLevel(player.getFoodLevel());
        entity.setExhaustion(player.getExhaustion());
        entity.setSaturation(player.getSaturation());
        entity.setFireTicks(player.getFireTicks());
        entity.getInventory().setContents(player.getInventory().getContents());
        entity.getInventory().setArmorContents(player.getInventory().getArmorContents());
        entity.addPotionEffects(player.getActivePotionEffects());

        entity.setMetadata("NPC", new FixedMetadataValue(plugin, true));

        // Play a nice little effect indicating the NPC was spawned
        if (plugin.getSettings().playEffect()) {
            Location l = entity.getLocation();
            l.getWorld().playEffect(l, Effect.MOBSPAWNER_FLAMES, 0, 64);
            // NOTE: Do not directly access the values in the sound enum, as that can change across versions\
            l.getWorld().playSound(l, EXPLODE_SOUND, 0.9F, 0);
        }

        // Create and start the NPCs despawn task
        long despawnTime = System.currentTimeMillis() + plugin.getSettings().getNpcDespawnMillis();
        NpcDespawnTask despawnTask = new NpcDespawnTask(plugin, npc, despawnTime);
        despawnTask.start();
        despawnTasks.put(npc, despawnTask);

        return npc;
    }

    public void despawn(Npc npc) {
        despawn(npc, NpcDespawnReason.DESPAWN);
    }

    public void despawn(Npc npc, NpcDespawnReason reason) {
        // Do nothing if NPC isn't spawned or if it's a different NPC
        Npc other = getSpawnedNpc(npc.getIdentity().getId());
        if (other == null || other != npc) return;

        // Call NPC despawn event
        NpcDespawnEvent event = new NpcDespawnEvent(npc, reason);
        Bukkit.getPluginManager().callEvent(event);

        // Cancel the NPCs despawn task if they have one
        if (hasDespawnTask(npc)) {
            NpcDespawnTask despawnTask = getDespawnTask(npc);
            despawnTask.stop();
            despawnTasks.remove(npc);
        }

        // Remove the NPC entity from the world
        npc.getEntity().remove();
        plugin.getNpcPlayerHelper().despawn(npc.getEntity());
        spawnedNpcs.remove(npc.getIdentity().getId());
        npc.getEntity().removeMetadata("NPC", plugin);
    }

    public Npc getSpawnedNpc(UUID playerId) {
        return spawnedNpcs.get(playerId);
    }

    public boolean npcExists(UUID playerId) {
        return spawnedNpcs.containsKey(playerId);
    }

    public NpcDespawnTask getDespawnTask(Npc npc) {
        return despawnTasks.get(npc);
    }

    public boolean hasDespawnTask(Npc npc) {
        return despawnTasks.containsKey(npc);
    }

    // Use reflection
    private static final Sound EXPLODE_SOUND;
    static {
        Sound sound;
        try {
            sound = Sound.valueOf("ENTITY_GENERIC_EXPLODE"); // 1.9 name
        } catch (IllegalArgumentException e) {
            try {
                sound = Sound.valueOf("EXPLODE"); // 1.8 name
            } catch (IllegalArgumentException e2) {
                throw new AssertionError("Unable to find explosion sound");
            }
        }
        EXPLODE_SOUND = sound;
    }

}
