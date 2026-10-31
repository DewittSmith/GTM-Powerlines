package com.github.dewittsmith.gtm_powerlines;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WireCutSelection {
    public record Selection(BlockPos pos) {
    }

    private static final Map<UUID, Selection> SELECTED = new HashMap<>();

    public static Selection get(Player p) {
        return SELECTED.get(p.getUUID());
    }

    public static void set(Player p, BlockPos pos) {
        SELECTED.put(p.getUUID(), new Selection(pos.immutable()));
    }

    public static void clear(Player p) {
        SELECTED.remove(p.getUUID());
    }
}