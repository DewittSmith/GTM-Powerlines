package com.github.dewittsmith.gtm_powerlines.util;

import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Collection;
import java.util.Set;

public class PredicateBlockTags extends SimplePredicate {
    private Set<TagKey<Block>> tags = null;

    public PredicateBlockTags() {
        super("tags");
    }

    public PredicateBlockTags(Collection<TagKey<Block>> tags) {
        this();
        this.tags = Set.copyOf(tags);
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        if (tags == null) {
            predicate = state -> false;
            candidates = () -> new BlockInfo[]{BlockInfo.fromBlock(Blocks.BARRIER)};
            return this;
        }

        predicate = state -> {
            for (TagKey<Block> tag : tags) {
                if (state.getBlockState().is(tag)) {
                    return true;
                }
            }

            return false;
        };

        candidates = () -> BuiltInRegistries.BLOCK.getTags()
                .filter(pair -> tags.contains(pair.getFirst()))
                .map(Pair::getSecond)
                .flatMap(HolderSet.Named::stream)
                .map(Holder::value)
                .map(BlockInfo::fromBlock)
                .toArray(BlockInfo[]::new);

        return this;
    }
}
