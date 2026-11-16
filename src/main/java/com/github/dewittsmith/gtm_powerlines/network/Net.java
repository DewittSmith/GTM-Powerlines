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

    private static final String PROTOCOL = "7";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(GTMPowerlines.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static boolean registered = false;

    public static synchronized void init() {
        if (registered) return;
        registered = true;

        var id = 0;
        CHANNEL.registerMessage(id++, WireFullSyncPacket.class,
                WireFullSyncPacket::encode, WireFullSyncPacket::decode, WireFullSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WireDeltaPacket.class,
                WireDeltaPacket::encode, WireDeltaPacket::decode, WireDeltaPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WireStatsRequestPacket.class,
                WireStatsRequestPacket::encode, WireStatsRequestPacket::decode, WireStatsRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, WireStatsPacket.class,
                WireStatsPacket::encode, WireStatsPacket::decode, WireStatsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ObstructionHighlightPacket.class,
                ObstructionHighlightPacket::encode, ObstructionHighlightPacket::decode,
                ObstructionHighlightPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MultimeterPathPacket.class,
                MultimeterPathPacket::encode, MultimeterPathPacket::decode, MultimeterPathPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WireAttachmentPacket.class,
                WireAttachmentPacket::encode, WireAttachmentPacket::decode, WireAttachmentPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToDimension(ServerLevel level, Object packet) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }
}
