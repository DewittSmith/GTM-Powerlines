package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;

import java.util.List;

import javax.annotation.Nullable;

/** Client-side state of the last multimeter measurement, rendered as a fading line strip. */
public final class MultimeterHighlight {

    public record Path(List<BlockPos> insulators, long startMillis) {}

    private static @Nullable Path current;

    public static void show(List<BlockPos> insulators) {
        current = insulators.size() < 2 ? null : new Path(List.copyOf(insulators), System.currentTimeMillis());
    }

    public static @Nullable Path get() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
