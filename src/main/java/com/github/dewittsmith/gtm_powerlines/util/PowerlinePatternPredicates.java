package com.github.dewittsmith.gtm_powerlines.util;

import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;

import static com.gregtechceu.gtceu.api.pattern.Predicates.custom;

public class PowerlinePatternPredicates {

    public static TraceabilityPredicate track(String name, BlockState blockState, Property<?>... props) {
        return custom(state -> {
            var bs = state.getBlockState();
            if (!bs.is(blockState.getBlock())) return false;
            for (Property<?> prop : props) {
                if (!bs.hasProperty(prop)) {
                    return false;
                }

                if (bs.getValue(prop) != blockState.getValue(prop)) {
                    return false;
                }
            }

            state.getMatchContext().getOrCreate(name, () -> new ArrayList<BlockPos>()).add(state.getPos());
            return true;
        }, () -> new BlockInfo[]{new BlockInfo(blockState)});
    }
}
