package com.github.dewittsmith.gtm_powerlines.graph;

import com.gregtechceu.gtceu.api.capability.forge.GTCapability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PowerlineNetwork extends SavedData {

    @Getter
    private final PowerlineGraph graph;

    private final ServerLevel level;

    private final Long2LongOpenHashMap startToEnd = new Long2LongOpenHashMap();
    private final Long2ObjectMap<List<TransferPath>> paths = new Long2ObjectOpenHashMap<>();

    public PowerlineNetwork(PowerlineGraph graph, ServerLevel level) {
        this.graph = graph;
        this.level = level;
    }

    private static ArrayList<TransferPath> arrayFactory(long key) {
        return new ArrayList<>();
    }

    @NonNull
    public static PowerlineNetwork get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(
                        tag -> PowerlineNetwork.load(level, tag),
                        () -> new PowerlineNetwork(new PowerlineGraph(), level),
                        "gtm_powerlines_network");
    }

    public boolean rebuildPath(BlockPos start, BlockPos end) {
        var key = start.asLong();

        if (!startToEnd.containsKey(key)) {
            startToEnd.put(key, end.asLong());
            setDirty();
        }

        var list = paths.computeIfAbsent(key, PowerlineNetwork::arrayFactory);
        for (var path : list) {
            if (path.getEnd().equals(end)) {
                return path.rebuild(graph);
            }
        }

        var path = new TransferPath(start, end);
        list.add(path);

        return path.rebuild(graph);
    }

    public void rebuildPaths() {
        for (var entry : startToEnd.long2LongEntrySet()) {
            var path = new TransferPath(BlockPos.of(entry.getLongKey()), BlockPos.of(entry.getLongValue()));
            rebuildPath(path.getStart(), path.getEnd());
        }
    }

    public long sendEnergy(BlockPos start, long voltage, long amperage) {
        var list = paths.getOrDefault(start.asLong(), Collections.emptyList());
        if (list.isEmpty()) return 0L;

        var usedAmperes = 0L;
        for (var path : list) {
            if (!path.hasPath()) continue;
            var v = Mth.floor(voltage - path.getTotalLoss());
            if (v <= 0) continue;
            var end = path.getEdges().get(path.getEdges().size() - 1);
            usedAmperes += sendEnergyTo(path.getEnd(), end.dirB(), v, amperage - usedAmperes);
            if (usedAmperes >= amperage) break;
        }

        return usedAmperes;
    }

    private long sendEnergyTo(BlockPos target, Direction side, long voltage, long amperage) {
        if (!level.isLoaded(target)) return 0L;
        var be = level.getBlockEntity(target);
        if (be == null) return 0L;
        var container = be.getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, side).resolve();
        return container.map(c -> c.acceptEnergyFromNetwork(side, voltage, amperage)).orElse(0L);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag = PowerlineGraph.serialize(graph, tag);

        var list = new ListTag();
        for (var entry : startToEnd.long2LongEntrySet()) {
            var e = new CompoundTag();
            e.putLong("start", entry.getLongKey());
            e.putLong("end", entry.getLongValue());
            list.add(e);
        }

        tag.put("startToEnd", list);
        return tag;
    }

    public static PowerlineNetwork load(ServerLevel level, CompoundTag tag) {
        var graph = PowerlineGraph.deserialize(tag);
        var result = new PowerlineNetwork(graph, level);

        for (var raw : tag.getList("startToEnd", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            var start = t.getLong("start");
            var end = t.getLong("end");
            result.startToEnd.put(start, end);
        }

        return result;
    }
}
