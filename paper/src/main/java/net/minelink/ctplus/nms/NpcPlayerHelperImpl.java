package net.minelink.ctplus.nms;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minelink.ctplus.compat.base.NpcIdentity;
import net.minelink.ctplus.compat.base.NpcPlayerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class NpcPlayerHelperImpl implements NpcPlayerHelper {
    @Override
    public Player spawn(Player player) {
        ServerLevel worldServer = ((CraftWorld) player.getWorld()).getHandle();

        NpcPlayer npcPlayer = NpcPlayer.valueOf(player);

        Location location = player.getLocation();

        npcPlayer.setPos(location.getX(), location.getY(), location.getZ());
        npcPlayer.setXRot(location.getPitch());
        npcPlayer.setYRot(location.getYaw());
        npcPlayer.setYHeadRot(location.getYaw());

        npcPlayer.getBukkitEntity().setNoDamageTicks(0);

        // Cast the player to the CraftPlayer to access NMS methods
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();

        // Remove the player entity with the appropriate removal reason
        nmsPlayer.remove(Entity.RemovalReason.DISCARDED);

        worldServer.addNewPlayer(npcPlayer);
        showAll(npcPlayer);

        return npcPlayer.getBukkitEntity();
    }

    public static void showAll(ServerPlayer entityPlayer) {
        ClientboundPlayerInfoUpdatePacket playerInfoAdd = new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, entityPlayer);
        ClientboundAddEntityPacket namedEntitySpawn = new ClientboundAddEntityPacket(entityPlayer.getId(),
                entityPlayer.getUUID(), entityPlayer.getX(), entityPlayer.getY(), entityPlayer.getZ(),
                ((entityPlayer.getXRot() * 256f) / 360f), ((entityPlayer.getYRot() * 256f) / 360f), EntityType.PLAYER, 0, Vec3.ZERO, ((entityPlayer.getYRot() * 256f) / 360f));
        ClientboundRotateHeadPacket headRotation = new ClientboundRotateHeadPacket(entityPlayer, (byte) ((entityPlayer.getYRot() * 256f) / 360f));
        ClientboundPlayerInfoRemovePacket playerInfoRemove = new ClientboundPlayerInfoRemovePacket(Collections.singletonList(entityPlayer.getUUID()));

        for (Player player : Bukkit.getOnlinePlayers()) {
            ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
            connection.send(playerInfoAdd);
            connection.send(namedEntitySpawn);
            //connection.send(headRotation);
            //connection.send(playerInfoRemove);
        }
    }

    @Override
    public void despawn(Player player) {
        ServerPlayer entity = ((CraftPlayer) player).getHandle();
        if (!(entity instanceof NpcPlayer)) {
            throw new IllegalArgumentException();
        }

        removePlayerList(player);
        ServerLevel worldServer = ((CraftWorld) player.getWorld()).getHandle();
        worldServer.chunkSource.removeEntity(entity);
        worldServer.getPlayers(serverPlayer -> serverPlayer instanceof NpcPlayer).remove(entity);
    }

    @Override
    public boolean isNpc(Player player) {
        return ((CraftPlayer) player).getHandle() instanceof NpcPlayer;
    }

    @Override
    public NpcIdentity getIdentity(Player player) {
        if (!isNpc(player)) {
            throw new IllegalArgumentException();
        }

        return ((NpcPlayer) ((CraftPlayer) player).getHandle()).getNpcIdentity();
    }

    @Override
    public void updateEquipment(Player player) {
        ServerPlayer entity = ((CraftPlayer) player).getHandle();

        if (!(entity instanceof NpcPlayer)) {
            throw new IllegalArgumentException("Entity is not an instance of NpcPlayer.");
        }
        AttributeMap attributeMap = entity.getAttributes();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = entity.getItemBySlot(slot);

            if (item == null) continue;
            Multimap<Holder<Attribute>, AttributeModifier> attributeModifiers = ArrayListMultimap.create();
            item.forEachModifier(slot, attributeModifiers::put);
            attributeMap.removeAttributeModifiers(attributeModifiers);
            attributeMap.addTransientAttributeModifiers(attributeModifiers);
            List<Pair<EquipmentSlot, ItemStack>> equipmentList = Lists.newArrayList();
            equipmentList.add(Pair.of(slot, item));
            ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(entity.getId(), equipmentList);
            ((CraftWorld) player.getWorld()).getHandle().getChunkSource().broadcast(entity, packet);
        }
    }

    @Override
    public void syncOffline(Player player) {
        ServerPlayer entity = ((CraftPlayer) player).getHandle();

        if (!(entity instanceof NpcPlayer npcPlayer)) {
            throw new IllegalArgumentException();
        }

        NpcIdentity identity = npcPlayer.getNpcIdentity();
        Player p = Bukkit.getPlayer(identity.getId());
        if (p != null && p.isOnline()) return;

        PlayerDataStorage worldStorage = ((CraftWorld) Bukkit.getWorlds().getFirst()).getHandle().getServer().playerDataStorage;
        Optional<CompoundTag> playerNbtOptional = worldStorage.load(entity);
        if (playerNbtOptional.isEmpty()) return;

        CompoundTag playerNbt = playerNbtOptional.get();

        //Field foodTickTimerField;
        //int foodTickTimer;

        //try {
        //    foodTickTimerField = FoodData.class.getDeclaredField("d");
        //    foodTickTimerField.setAccessible(true);
        //    foodTickTimer = foodTickTimerField.getInt(entity.getFoodData());
        //} catch (NoSuchFieldException | IllegalAccessException e) {
        //    throw new RuntimeException(e);
        //}

        playerNbt.putShort("Air", (short) npcPlayer.getAirSupply());
        playerNbt.putFloat("Health", entity.getHealth());
        playerNbt.putFloat("AbsorptionAmount", entity.getAbsorptionAmount());
        playerNbt.putInt("XpTotal", entity.experienceLevel);
        playerNbt.putInt("foodLevel", entity.getFoodData().getFoodLevel());
        //playerNbt.putInt("foodTickTimer", foodTickTimer);
        playerNbt.putFloat("foodSaturationLevel", entity.getFoodData().getSaturationLevel());
        playerNbt.putFloat("foodExhaustionLevel", entity.getFoodData().exhaustionLevel);
        playerNbt.putInt("Fire", entity.getRemainingFireTicks());

        // Add Position and Rotation
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(entity.position().x));
        position.add(DoubleTag.valueOf(entity.position().y));
        position.add(DoubleTag.valueOf(entity.position().z));
        playerNbt.put("Pos", position);

        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(entity.getYRot())); // Yaw
        rotation.add(FloatTag.valueOf(entity.getXRot())); // Pitch
        playerNbt.put("Rotation", rotation);

        // Dimension
        playerNbt.putString("Dimension", entity.level().dimension().location().toString());

        // Inventory
        playerNbt.put("Inventory", npcPlayer.getInventory().save(new ListTag()));

        File file1 = new File(worldStorage.getPlayerDir(), identity.getId().toString() + ".dat.tmp");
        File file2 = new File(worldStorage.getPlayerDir(), identity.getId().toString() + ".dat");

        try {
            NbtIo.writeCompressed(playerNbt, new FileOutputStream(file1));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save player data for " + identity.getName(), e);
        }

        if ((!file2.exists() || file2.delete()) && !file1.renameTo(file2)) {
            throw new RuntimeException("Failed to save player data for " + identity.getName());
        }
    }

    @Override
    public void createPlayerList(Player player) {
        ServerPlayer p = ((CraftPlayer) player).getHandle();

        for (ServerPlayer serverPlayer : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, serverPlayer);
            p.connection.send(packet);
        }
    }

    @Override
    public void removePlayerList(Player player) {
        ServerPlayer p = ((CraftPlayer) player).getHandle();
        for (ServerPlayer serverPlayer : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(Collections.singletonList(serverPlayer.getUUID()));
            p.connection.send(packet);
        }
    }
}
