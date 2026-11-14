package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;

import java.util.List;

import javax.annotation.Nullable;

/** Client-side state of the last obstruction, rendered as fading red outlines. */
public final class ObstructionHighlight {

    /**
     * @param blocks blocks that blocked a new wire
     * @param wires  endpoint pairs of wires that blocked a new block
     */
    public record Highlight(List<BlockPos> blocks, List<BlockPos> wires, long startMillis) {}

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
}
