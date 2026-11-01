package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;

public interface IWireMachine {
    int getMaxConnections();

    float getWireDistanceFor(BlockPos block);
}
