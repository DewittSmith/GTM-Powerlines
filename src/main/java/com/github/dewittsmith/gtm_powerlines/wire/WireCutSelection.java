package com.github.dewittsmith.gtm_powerlines.wire;

import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.WireCutSelectionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WireCutSelection {
    public record Selection(BlockPos pos) {
    }

    private static final Map<UUID, Selection> SELECTED = new HashMap<>();
    private static @Nullable Selection clientSelection;

    public static Selection get(Player p) {
        return p.level().isClientSide ? clientSelection : SELECTED.get(p.getUUID());
    }

    public static void set(Player p, BlockPos pos) {
        var selection = new Selection(pos.immutable());
        SELECTED.put(p.getUUID(), selection);
        if (p instanceof ServerPlayer sp) Net.sendToPlayer(sp, new WireCutSelectionPacket(selection));
    }

    public static void clear(Player p) {
        SELECTED.remove(p.getUUID());
        if (p instanceof ServerPlayer sp) Net.sendToPlayer(sp, new WireCutSelectionPacket(null));
    }

    public static void setClient(@Nullable Selection selection) {
        clientSelection = selection;
    }
}