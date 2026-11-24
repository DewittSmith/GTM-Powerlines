package com.github.dewittsmith.gtm_powerlines.util;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;

public final class PowerlinePredicates {

    private static final PartAbility[] INPUTS = { PartAbility.INPUT_ENERGY, PartAbility.SUBSTATION_INPUT_ENERGY };
    private static final PartAbility[] OUTPUTS = { PartAbility.OUTPUT_ENERGY, PartAbility.SUBSTATION_OUTPUT_ENERGY };

    public static MultiPredicate powerlineHatch() {
        return abilities(INPUTS).or(abilities(OUTPUTS))
                .addTooltips(Component.translatable("multiblock.gtm_powerlines.pattern.error.mixed_hatches"));
    }

    public static IO hatchIO(Block block) {
        for (var ability : INPUTS) if (ability.isApplicable(block)) return IO.IN;
        for (var ability : OUTPUTS) if (ability.isApplicable(block)) return IO.OUT;
        return IO.NONE;
    }

    public static MultiPredicate matchingStates(BlockState blockState, Property<?>... props) {
        return builder("PowerlineInsulator")
                .predicate(ctx -> {
                    var state = ctx.state();
                    if (!state.is(blockState.getBlock())) return false;
                    for (var prop : props) {
                        if (!state.hasProperty(prop) || state.getValue(prop) != blockState.getValue(prop)) return false;
                    }
                    return true;
                })
                .states(blockState)
                .toMultiPredicate();
    }
}
