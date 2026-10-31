package com.github.dewittsmith.gtm_powerlines.events;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WireAttachment {
    public record Attach(ResourceKey<Level> dimension, BlockPos pos, Item wire, int argb) {
    }

    private static final Map<UUID, Attach> ATTACH = new HashMap<>();

    public static Attach get(Player p) {
        return ATTACH.get(p.getUUID());
    }

    public static void set(Player p, Level l, BlockPos pos, Item wire, int argb) {
        ATTACH.put(p.getUUID(), new Attach(l.dimension(), pos.immutable(), wire, argb));
    }

    public static void clear(Player p) {
        ATTACH.remove(p.getUUID());
    }

    public static void clearAll() {
        ATTACH.clear();
    }
}
