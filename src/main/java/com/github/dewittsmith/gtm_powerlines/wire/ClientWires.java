package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;

import com.github.dewittsmith.gtm_powerlines.network.PowerlineNetwork;

import java.util.*;

public final class ClientWires {

    private static final Set<PowerlineNetwork.Edge> EDGES = new LinkedHashSet<>();

    public static void replace(List<PowerlineNetwork.Edge> all) {
        EDGES.clear();
        EDGES.addAll(all);
    }

    public static void apply(boolean add, PowerlineNetwork.Edge e) {
        if (add) EDGES.add(e);
        else EDGES.remove(e);
    }

    public static List<PowerlineNetwork.Edge> at(BlockPos pos) {
        List<PowerlineNetwork.Edge> out = new ArrayList<>();
        for (var e : EDGES) if (e.a().equals(pos) || e.b().equals(pos)) out.add(e);
        return out;
    }

    public static Collection<PowerlineNetwork.Edge> all() {
        return EDGES;
    }

    public static void clear() {
        EDGES.clear();
    }
}
