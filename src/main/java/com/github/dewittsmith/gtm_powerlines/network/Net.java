package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;

import java.util.Optional;

public final class Net {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(GTMPowerlines.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static boolean registered = false;

    public static synchronized void init() {
        if (registered) return;
        registered = true;

        int id = 0;
        CHANNEL.registerMessage(id++, WireFullSyncPacket.class,
                WireFullSyncPacket::encode, WireFullSyncPacket::decode, WireFullSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WireDeltaPacket.class,
                WireDeltaPacket::encode, WireDeltaPacket::decode, WireDeltaPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WireAttachmentPacket.class,
                WireAttachmentPacket::encode, WireAttachmentPacket::decode, WireAttachmentPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WireCutSelectionPacket.class,
                WireCutSelectionPacket::encode, WireCutSelectionPacket::decode, WireCutSelectionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToDimension(ServerLevel level, Object packet) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }
}
