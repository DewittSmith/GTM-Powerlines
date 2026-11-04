package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record WireFullSyncPacket(List<PowerlineNetwork.Edge> edges) {

    public static void encode(WireFullSyncPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.edges.size());
        for (var e : p.edges) writeEdge(buf, e);
    }

    public static WireFullSyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<PowerlineNetwork.Edge> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(readEdge(buf));
        return new WireFullSyncPacket(list);
    }

    public static void handle(WireFullSyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get()
                .enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientWires.replace(p.edges)));
        ctx.get().setPacketHandled(true);
    }

    public static void writeEdge(FriendlyByteBuf buf, PowerlineNetwork.Edge e) {
        buf.writeBlockPos(e.a());
        buf.writeBlockPos(e.b());
        buf.writeResourceLocation(e.wire());
    }

    public static PowerlineNetwork.Edge readEdge(FriendlyByteBuf buf) {
        return PowerlineNetwork.Edge.of(buf.readBlockPos(), buf.readBlockPos(), buf.readResourceLocation());
    }
}
