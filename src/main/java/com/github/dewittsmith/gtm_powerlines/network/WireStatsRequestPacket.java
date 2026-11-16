package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;

import java.util.function.Supplier;

public record WireStatsRequestPacket(BlockPos a, BlockPos b) {

    private static final double MAX_DISTANCE = 128;

    public static void encode(WireStatsRequestPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.a);
        buf.writeBlockPos(p.b);
    }

    public static WireStatsRequestPacket decode(FriendlyByteBuf buf) {
        return new WireStatsRequestPacket(buf.readBlockPos(), buf.readBlockPos());
    }

    public static void handle(WireStatsRequestPacket p, Supplier<NetworkEvent.Context> ctx) {
        var player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (player == null) return;
            if (player.blockPosition().distSqr(p.a) > MAX_DISTANCE * MAX_DISTANCE) return;
            var network = PowerlineNetwork.get(player.serverLevel());
            network.getGraph().getEdge(p.a, p.b).ifPresent(edge -> Net.sendToPlayer(player,
                    new WireStatsPacket(edge.a(), edge.b(), network.getCurrentVoltage(edge),
                            network.getAverageAmperage(edge), network.getTemperature(edge))));
        });
        ctx.get().setPacketHandled(true);
    }
}
