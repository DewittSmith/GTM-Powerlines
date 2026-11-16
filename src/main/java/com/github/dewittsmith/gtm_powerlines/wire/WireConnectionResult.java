package com.github.dewittsmith.gtm_powerlines.wire;

import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;

public record WireConnectionResult(Status status, int value, PowerlineGraph.Edge edge) {

    public static final WireConnectionResult SAME_BLOCK = new WireConnectionResult(Status.SAME_BLOCK, 0, null);
    public static final WireConnectionResult ALREADY_CONNECTED = new WireConnectionResult(Status.ALREADY_CONNECTED, 0,
            null);
    public static final WireConnectionResult TOO_MANY_CONNECTIONS = new WireConnectionResult(
            Status.TOO_MANY_CONNECTIONS, 0, null);

    public static WireConnectionResult success(PowerlineGraph.Edge edge) {
        return new WireConnectionResult(Status.SUCCESS, 0, edge);
    }

    public static WireConnectionResult incompatibleTier(int requiredTier) {
        return new WireConnectionResult(Status.INCOMPATIBLE_TIER, requiredTier, null);
    }

    public enum Status {
        SUCCESS,
        SAME_BLOCK,
        ALREADY_CONNECTED,
        TOO_MANY_CONNECTIONS,
        INCOMPATIBLE_TIER
    }
}
