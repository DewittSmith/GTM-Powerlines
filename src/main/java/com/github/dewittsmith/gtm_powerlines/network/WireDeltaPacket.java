package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;

import java.util.function.Supplier;

import static com.github.dewittsmith.gtm_powerlines.network.PowerlineNetwork.Edge;

public record WireDeltaPacket(boolean add, Edge edge) {

    public static void encode(WireDeltaPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.add);
        WireFullSyncPacket.writeEdge(buf, p.edge);
    }

    public static WireDeltaPacket decode(FriendlyByteBuf buf) {
        return new WireDeltaPacket(buf.readBoolean(), WireFullSyncPacket.readEdge(buf));
    }

    public static void handle(WireDeltaPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(
                () -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientWires.apply(p.add, p.edge)));
        ctx.get().setPacketHandled(true);
    }
}
