package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;

import java.util.function.Supplier;

public record WireDeltaPacket(Op op, PowerlineGraph.Edge edge) {

    public enum Op {
        ADD,
        REMOVE,
        UPDATE
    }

    public static void encode(WireDeltaPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.op);
        WireFullSyncPacket.writeEdge(buf, p.edge);
    }

    public static WireDeltaPacket decode(FriendlyByteBuf buf) {
        return new WireDeltaPacket(buf.readEnum(Op.class), WireFullSyncPacket.readEdge(buf));
    }

    public static void handle(WireDeltaPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(
                () -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientWires.apply(p.op, p.edge)));
        ctx.get().setPacketHandled(true);
    }
}
