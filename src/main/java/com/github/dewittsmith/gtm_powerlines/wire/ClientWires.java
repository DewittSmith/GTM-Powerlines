package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;

import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.network.WireDeltaPacket;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.Collection;
import java.util.List;

public final class ClientWires {

    private static final PowerlineGraph GRAPH = new PowerlineGraph();

    public static void replace(List<PowerlineGraph.Edge> all) {
        GRAPH.clear();
        all.forEach(GRAPH::add);
    }

    public static void apply(WireDeltaPacket.Op op, PowerlineGraph.Edge e) {
        switch (op) {
            case ADD -> GRAPH.add(e);
            case REMOVE -> GRAPH.disconnect(e.a(), e.b());
            case UPDATE -> GRAPH.replace(e);
        }
    }

    public static PowerlineGraph graph() {
        return GRAPH;
    }

    public static List<PowerlineGraph.Edge> at(BlockPos pos) {
        return GRAPH.atBlock(pos);
    }

    public static Collection<PowerlineGraph.Edge> all() {
        return GRAPH.all();
    }

    public static Long2ObjectMap<List<PowerlineGraph.Edge>> byChunk() {
        return GRAPH.byChunk();
    }

    public static void clear() {
        GRAPH.clear();
    }
}
