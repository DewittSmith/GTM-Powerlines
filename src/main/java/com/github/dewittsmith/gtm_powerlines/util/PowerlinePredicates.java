package com.github.dewittsmith.gtm_powerlines.util;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Stream;

import static com.gregtechceu.gtceu.api.pattern.Predicates.custom;

public final class PowerlinePredicates {

    public static final String IO_KEY = "PowerlineIO";
    private static final PartAbility[] INPUTS = { PartAbility.INPUT_ENERGY, PartAbility.SUBSTATION_INPUT_ENERGY };
    private static final PartAbility[] OUTPUTS = { PartAbility.OUTPUT_ENERGY, PartAbility.SUBSTATION_OUTPUT_ENERGY };

    public static TraceabilityPredicate powerlineHatch() {
        return new TraceabilityPredicate(state -> {
            var block = state.getBlockState().getBlock();

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
        for (var a : abilities) if (a.isApplicable(block)) return true;
        return false;
    }

    public static TraceabilityPredicate track(String name, BlockState blockState, Property<?>... props) {
        return custom(state -> {
            var bs = state.getBlockState();
            if (!bs.is(blockState.getBlock())) return false;
            for (var prop : props) {
                if (!bs.hasProperty(prop)) {
                    return false;
                }

                if (bs.getValue(prop) != blockState.getValue(prop)) {
                    return false;
                }
            }

            state.getMatchContext().getOrCreate(name, () -> new ArrayList<BlockPos>()).add(state.getPos());
            return true;
        }, () -> new BlockInfo[] { new BlockInfo(blockState) });
    }
}
