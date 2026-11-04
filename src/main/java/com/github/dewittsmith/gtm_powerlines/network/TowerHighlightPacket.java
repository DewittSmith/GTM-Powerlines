package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.dewittsmith.gtm_powerlines.renderer.ClientTowerHighlight;

import java.util.function.Supplier;

public record TowerHighlightPacket(ResourceKey<Level> dimension, long[] structure, long[] insulators, long controller) {

    public static void encode(TowerHighlightPacket p, FriendlyByteBuf buf) {
        buf.writeResourceKey(p.dimension);
        buf.writeLongArray(p.structure);
        buf.writeLongArray(p.insulators);
        buf.writeLong(p.controller);
    }

    public static TowerHighlightPacket decode(FriendlyByteBuf buf) {
        return new TowerHighlightPacket(buf.readResourceKey(Registries.DIMENSION),
                buf.readLongArray(), buf.readLongArray(), buf.readLong());
    }

    public static void handle(TowerHighlightPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientTowerHighlight.set(p)));
        ctx.get().setPacketHandled(true);
    }
}
