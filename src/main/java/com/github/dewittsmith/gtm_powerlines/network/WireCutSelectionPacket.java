package com.github.dewittsmith.gtm_powerlines.network;

import com.github.dewittsmith.gtm_powerlines.wire.WireCutSelection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public record WireCutSelectionPacket(@Nullable WireCutSelection.Selection selection) {

    public static void encode(WireCutSelectionPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.selection != null);
        if (p.selection != null) buf.writeBlockPos(p.selection.pos());
    }

    public static WireCutSelectionPacket decode(FriendlyByteBuf buf) {
        return new WireCutSelectionPacket(buf.readBoolean() ? new WireCutSelection.Selection(buf.readBlockPos()) : null);
    }

    public static void handle(WireCutSelectionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WireCutSelection.setClient(p.selection)));
        ctx.get().setPacketHandled(true);
    }
}
