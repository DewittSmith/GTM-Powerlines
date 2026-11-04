package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.dewittsmith.gtm_powerlines.wire.WireAttachment;

import java.util.function.Supplier;

import javax.annotation.Nullable;

public record WireAttachmentPacket(@Nullable WireAttachment.Attach attachment) {

    public static void encode(WireAttachmentPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.attachment != null);
        if (p.attachment == null) return;

        buf.writeResourceKey(p.attachment.dimension());
        buf.writeBlockPos(p.attachment.pos());
        buf.writeItem(new ItemStack(p.attachment.wire()));
        buf.writeInt(p.attachment.argb());
    }

    public static WireAttachmentPacket decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return new WireAttachmentPacket(null);

        return new WireAttachmentPacket(new WireAttachment.Attach(
                buf.readResourceKey(Registries.DIMENSION),
                buf.readBlockPos(),
                buf.readItem().getItem(),
                buf.readInt()));
    }

    public static void handle(WireAttachmentPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(
                () -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WireAttachment.setClient(p.attachment)));
        ctx.get().setPacketHandled(true);
    }
}
