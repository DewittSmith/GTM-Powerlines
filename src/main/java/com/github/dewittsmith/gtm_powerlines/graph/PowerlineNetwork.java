package com.github.dewittsmith.gtm_powerlines.graph;

import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.WireDeltaPacket;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.common.pipelike.cable.EnergyNetHandler;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PowerlineNetwork extends SavedData {

    @Getter
    private final PowerlineGraph graph;

    private final ServerLevel level;

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
        for (var a : graph.allNodes()) {
            for (var b : graph.allNodes()) {
                if (a.equals(b)) continue;
                rebuildPath(a, b);
            }
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

    public boolean connect(BlockPos start, Direction startDir, BlockPos end, Direction endDir, ResourceLocation wire) {
        if (!graph.connect(start, startDir, end, endDir, wire)) return false;

        var e = graph.getEdge(start, end).orElse(null);
        Net.sendToDimension(level, new WireDeltaPacket(true, e));

        setDirty();
        rebuildPaths();
        return true;
    }

    public boolean disconnect(BlockPos start, BlockPos end) {
        if (!graph.disconnect(start, end)) return false;

        var e = graph.getEdge(start, end).orElse(null);
        Net.sendToDimension(level, new WireDeltaPacket(false, e));

        setDirty();
        rebuildPaths();
        return true;
    }

    public List<PowerlineGraph.Edge> disconnectAll(BlockPos start) {
        var result = graph.disconnectAll(start);

        result.forEach(e -> Net.sendToDimension(level, new WireDeltaPacket(false, e)));

        setDirty();
        rebuildPaths();
        return result;
    }

    private long sendEnergyTo(BlockPos target, Direction side, long voltage, long amperage) {
        target = target.relative(side.getOpposite());

        if (!level.isLoaded(target)) return 0L;
        var be = level.getBlockEntity(target);
        if (be == null) return 0L;
        var container = be.getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, side).resolve();
        return container.map(c -> c.acceptEnergyFromNetwork(side, voltage, amperage)).orElse(0L);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        return PowerlineGraph.serialize(graph, tag);
    }

    public static PowerlineNetwork load(ServerLevel level, CompoundTag tag) {
        var graph = PowerlineGraph.deserialize(tag);
        var result = new PowerlineNetwork(graph, level);

        result.rebuildPaths();
        return result;
    }
}
