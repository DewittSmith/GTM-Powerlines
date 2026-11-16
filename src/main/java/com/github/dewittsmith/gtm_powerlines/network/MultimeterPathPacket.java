package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.dewittsmith.gtm_powerlines.wire.MultimeterHighlight;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record MultimeterPathPacket(List<BlockPos> insulators) {

    public static void encode(MultimeterPathPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.insulators.size());
        for (var pos : p.insulators) buf.writeBlockPos(pos);
    }

    public static MultimeterPathPacket decode(FriendlyByteBuf buf) {
        var n = buf.readVarInt();
        var list = new ArrayList<BlockPos>(n);
        for (var i = 0; i < n; i++) list.add(buf.readBlockPos());
        return new MultimeterPathPacket(list);
    }

    public static void handle(MultimeterPathPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(
                () -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MultimeterHighlight.show(p.insulators)));
        ctx.get().setPacketHandled(true);
    }
}
