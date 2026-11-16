package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;

import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.WireStatsPacket;
import com.github.dewittsmith.gtm_powerlines.network.WireStatsRequestPacket;

import javax.annotation.Nullable;

public final class ClientWireStats {

    private static final long REQUEST_INTERVAL = 500;

    private static @Nullable WireStatsPacket latest;
    private static @Nullable BlockPos requestedA, requestedB;
    private static long lastRequest;

    public static @Nullable WireStatsPacket get(PowerlineGraph.Edge edge) {
        long now = System.currentTimeMillis();
        boolean sameEdge = edge.a().equals(requestedA) && edge.b().equals(requestedB);
        if (!sameEdge || now - lastRequest > REQUEST_INTERVAL) {
            requestedA = edge.a();
            requestedB = edge.b();
            lastRequest = now;
            Net.sendToServer(new WireStatsRequestPacket(edge.a(), edge.b()));
        }
        var data = latest;
        return data != null && data.a().equals(edge.a()) && data.b().equals(edge.b()) ? data : null;
    }

    public static void update(WireStatsPacket packet) {
        latest = packet;
    }

    public static void clear() {
        latest = null;
        requestedA = requestedB = null;
    }
}
