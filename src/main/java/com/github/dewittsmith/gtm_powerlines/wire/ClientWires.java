package com.github.dewittsmith.gtm_powerlines.wire;

import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import net.minecraft.core.BlockPos;

import java.util.*;

public final class ClientWires {

    private static final Set<PowerlineGraph.Edge> EDGES = new LinkedHashSet<>();

    public static void replace(List<PowerlineGraph.Edge> all) {
        EDGES.clear();
        EDGES.addAll(all);
    }

    public static void apply(boolean add, PowerlineGraph.Edge e) {
        if (add) EDGES.add(e);
        else EDGES.remove(e);
    }

    public static List<PowerlineGraph.Edge> at(BlockPos pos) {
        var out = new ArrayList<PowerlineGraph.Edge>();
        for (var e : EDGES) if (e.a().equals(pos) || e.b().equals(pos)) out.add(e);
        return out;
    }

    public static Collection<PowerlineGraph.Edge> all() {
        return EDGES;
    }

    public static void clear() {
        EDGES.clear();
    }
}
