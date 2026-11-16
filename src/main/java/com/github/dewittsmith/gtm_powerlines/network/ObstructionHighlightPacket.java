package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.dewittsmith.gtm_powerlines.wire.ObstructionHighlight;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record ObstructionHighlightPacket(List<BlockPos> blocks, List<BlockPos> wires) {

    public static void encode(ObstructionHighlightPacket p, FriendlyByteBuf buf) {
        buf.writeCollection(p.blocks, FriendlyByteBuf::writeBlockPos);
        buf.writeCollection(p.wires, FriendlyByteBuf::writeBlockPos);
    }

    public static ObstructionHighlightPacket decode(FriendlyByteBuf buf) {
        return new ObstructionHighlightPacket(buf.readCollection(ArrayList::new, FriendlyByteBuf::readBlockPos),
                buf.readCollection(ArrayList::new, FriendlyByteBuf::readBlockPos));
    }

    public static void handle(ObstructionHighlightPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ObstructionHighlight.show(p.blocks, p.wires)));
        ctx.get().setPacketHandled(true);
    }
}
