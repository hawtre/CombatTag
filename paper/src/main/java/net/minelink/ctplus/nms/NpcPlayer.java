package net.minelink.ctplus.nms;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minelink.ctplus.CombatTagPlus;
import net.minelink.ctplus.compat.base.NpcIdentity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

public class NpcPlayer extends ServerPlayer {

    private NpcIdentity identity;
    public NpcPlayer(MinecraftServer server, ServerLevel world, GameProfile profile) {
        super(server, world, profile, ClientInformation.createDefault());
    }

    public NpcIdentity getNpcIdentity() {
        return identity;
    }

    public static NpcPlayer valueOf(Player player) {
        MinecraftServer minecraftServer = MinecraftServer.getServer();
        ServerLevel worldServer = ((CraftWorld) player.getWorld()).getHandle();
        GameProfile gameProfile = new GameProfile(player.getUniqueId(), player.getName());

        NpcPlayer npcPlayer = new NpcPlayer(minecraftServer, worldServer, gameProfile);
        npcPlayer.getEntityData().set(
                net.minecraft.world.entity.player.Player.DATA_PLAYER_MODE_CUSTOMISATION,
                (byte) 0x7F); // render all skin parts

        npcPlayer.identity = new NpcIdentity(player);
        npcPlayer.connection = new NpcPlayerConnection(npcPlayer);

        ProfileProperty property = player.getPlayerProfile().getProperties().iterator().next();
        String texture = property.getValue();
        String signature = property.getSignature();

        npcPlayer.getGameProfile().getProperties().put("textures", new Property("textures", texture, signature));

        return npcPlayer;
    }

    @Override
    public void tick(){
        super.tick();
        doTick();
    }

    @Override
    public boolean hurt(DamageSource damageSource, float f) {
        boolean damaged = super.hurt(damageSource, f);
        if (damaged) {
            if (this.hurtMarked) {
                this.hurtMarked = false;
                Bukkit.getScheduler().runTask(CombatTagPlus.getPlugin(CombatTagPlus.class), () -> NpcPlayer.this.hurtMarked = true);
            }
        }
        return damaged;
    }
}

