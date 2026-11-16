package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.WireAttachmentPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

public final class WireAttachment {

    private static final Map<UUID, Attach> ATTACH = new HashMap<>();
    private static @Nullable Attach clientAttachment;

    public static Attach get(Player p) {
        return p.level().isClientSide ? clientAttachment : ATTACH.get(p.getUUID());
    }

    public static void set(Player p, Level l, BlockPos pos, Item wire, WireStats stats) {
        var attachment = new Attach(l.dimension(), pos.immutable(), wire, stats);
        ATTACH.put(p.getUUID(), attachment);
        if (p instanceof ServerPlayer sp) Net.sendToPlayer(sp, new WireAttachmentPacket(attachment));
    }

    public static void clear(Player p) {
        ATTACH.remove(p.getUUID());
        if (p instanceof ServerPlayer sp) Net.sendToPlayer(sp, new WireAttachmentPacket(null));
    }

    public static void setClient(@Nullable Attach attachment) {
        clientAttachment = attachment;
    }

    public record Attach(ResourceKey<Level> dimension, BlockPos pos, Item wire, WireStats stats) {}
}
