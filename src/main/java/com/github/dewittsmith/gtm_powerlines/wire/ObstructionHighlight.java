package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;

import java.util.List;

import javax.annotation.Nullable;

public final class ObstructionHighlight {

    private static @Nullable Highlight current;

    public static void show(List<BlockPos> blocks, List<BlockPos> wires) {
        current = blocks.isEmpty() && wires.isEmpty() ? null :
                new Highlight(List.copyOf(blocks), List.copyOf(wires), System.currentTimeMillis());
    }

    public static @Nullable Highlight get() {
        return current;
    }

    public static void clear() {
        current = null;
    }

    public record Highlight(List<BlockPos> blocks, List<BlockPos> wires, long startMillis) {}
}
