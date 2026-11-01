package com.github.dewittsmith.gtm_powerlines.util;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.stream.Stream;

public final class PowerlinePredicates {
    private static final PartAbility[] INPUTS = {PartAbility.INPUT_ENERGY, PartAbility.SUBSTATION_INPUT_ENERGY};
    private static final PartAbility[] OUTPUTS = {PartAbility.OUTPUT_ENERGY, PartAbility.SUBSTATION_OUTPUT_ENERGY};

    public static final String IO_KEY = "PowerlineIO";

    public static TraceabilityPredicate powerlineHatch() {
        return new TraceabilityPredicate(state -> {
            Block block = state.getBlockState().getBlock();

            IO io;
            if (isAny(INPUTS, block)) io = IO.IN;
            else if (isAny(OUTPUTS, block)) io = IO.OUT;
            else return false;

            Object current = state.getMatchContext().getOrPut(IO_KEY, io);
            if (!current.equals(io)) {
                state.setError(new PatternStringError("multiblock.gtm_powerlines.pattern.error.mixed_hatches"));
                return false;
            }
            return true;
        }, () -> Stream.concat(Arrays.stream(INPUTS), Arrays.stream(OUTPUTS))
                .flatMap(a -> a.getAllBlocks().stream())
                .distinct()
                .map(b -> BlockInfo.fromBlockState(b.defaultBlockState()))
                .toArray(BlockInfo[]::new))
                .addTooltips(Component.translatable("multiblock.gtm_powerlines.pattern.error.mixed_hatches"));
    }

    private static boolean isAny(PartAbility[] abilities, Block block) {
        for (PartAbility a : abilities) if (a.isApplicable(block)) return true;
        return false;
    }
}