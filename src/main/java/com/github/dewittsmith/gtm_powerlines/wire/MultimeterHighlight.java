package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;

import java.util.List;

import javax.annotation.Nullable;

public final class MultimeterHighlight {

    private static @Nullable List<BlockPos> current;

    public static void show(List<BlockPos> insulators) {
        current = insulators.size() < 2 ? null : List.copyOf(insulators);
    }

    public static @Nullable List<BlockPos> get() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
