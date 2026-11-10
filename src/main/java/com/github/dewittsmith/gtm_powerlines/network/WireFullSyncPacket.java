package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record WireFullSyncPacket(List<PowerlineGraph.Edge> edges) {

    public static void encode(WireFullSyncPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.edges.size());
        for (var e : p.edges) writeEdge(buf, e);
    }

    public static WireFullSyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        var list = new ArrayList<PowerlineGraph.Edge>(n);
        for (int i = 0; i < n; i++) list.add(readEdge(buf));
        return new WireFullSyncPacket(list);
    }

    public static void handle(WireFullSyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get()
                .enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientWires.replace(p.edges)));
        ctx.get().setPacketHandled(true);
    }

    public static void writeEdge(FriendlyByteBuf buf, PowerlineGraph.Edge e) {
        buf.writeBlockPos(e.a());
        buf.writeEnum(e.dirA());
        buf.writeBlockPos(e.b());
        buf.writeEnum(e.dirB());
        buf.writeResourceLocation(e.wire());
    }

    public static PowerlineGraph.Edge readEdge(FriendlyByteBuf buf) {
        return PowerlineGraph.Edge.of(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readBlockPos(),
                buf.readEnum(Direction.class), buf.readResourceLocation());
    }
}
