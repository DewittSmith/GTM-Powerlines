package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.dewittsmith.gtm_powerlines.wire.ClientWireStats;

import java.util.function.Supplier;

public record WireStatsPacket(BlockPos a, BlockPos b, long voltage, double amperage, int temperature) {

    public static void encode(WireStatsPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.a);
        buf.writeBlockPos(p.b);
        buf.writeVarLong(p.voltage);
        buf.writeDouble(p.amperage);
        buf.writeVarInt(p.temperature);
    }

    public static WireStatsPacket decode(FriendlyByteBuf buf) {
        return new WireStatsPacket(buf.readBlockPos(), buf.readBlockPos(), buf.readVarLong(), buf.readDouble(),
                buf.readVarInt());
    }

    public static void handle(WireStatsPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientWireStats.update(p)));
        ctx.get().setPacketHandled(true);
    }
}
