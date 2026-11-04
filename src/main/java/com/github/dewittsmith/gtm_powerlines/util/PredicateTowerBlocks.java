package com.github.dewittsmith.gtm_powerlines.util;

import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.registries.BuiltInRegistries;

import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;

public class PredicateTowerBlocks extends SimplePredicate {

    public PredicateTowerBlocks() {
        super("tower_blocks");
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        predicate = state -> GTMPowerlinesConfig.INSTANCE.isTowerBlock(state.getBlockState());

        candidates = () -> BuiltInRegistries.BLOCK.stream()
                .filter(block -> GTMPowerlinesConfig.INSTANCE.isTowerBlock(block.defaultBlockState()))
                .map(BlockInfo::fromBlock)
                .toArray(BlockInfo[]::new);

        return this;
    }
}
