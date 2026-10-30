package com.github.dewittsmith.gtm_powerlines.trackers;

import com.github.dewittsmith.gtm_powerlines.machines.PowerlineTowerBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public final class PowerlineTowerTracker {
    private static final Map<ResourceKey<Level>, Long2ObjectMap<LongSet>> WATCHED = new HashMap<>();
    private static final Map<ResourceKey<Level>, LongSet> PENDING = new HashMap<>();

    public static void update(Level level, BlockPos tower, LongSet structure) {
        remove(level, tower);
        var map = WATCHED.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
        long towerKey = tower.asLong();
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        for (long s : structure) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        c.set(BlockPos.getX(s) + dx, BlockPos.getY(s) + dy, BlockPos.getZ(s) + dz);
                        map.computeIfAbsent(c.asLong(), k -> new LongOpenHashSet()).add(towerKey);
                    }
        }
    }

    public static void remove(Level level, BlockPos tower) {
        var map = WATCHED.get(level.dimension());
        if (map == null) return;
        long towerKey = tower.asLong();
        map.values().forEach(set -> set.remove(towerKey));
        map.values().removeIf(LongSet::isEmpty);
    }

    public static void onBlockChanged(Level level, BlockPos pos) {
        var map = WATCHED.get(level.dimension());
        if (map == null) return;
        LongSet towers = map.get(pos.asLong());
        if (towers != null) PENDING.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet()).addAll(towers);
    }

    public static void flush(ServerLevel level) {
        LongSet pending = PENDING.remove(level.dimension());
        if (pending == null) return;
        for (long key : pending) {
            BlockPos pos = BlockPos.of(key);
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PowerlineTowerBlockEntity tower) {
                tower.rescan();
            }
        }
    }
}