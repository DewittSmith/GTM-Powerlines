package com.github.dewittsmith.gtm_powerlines.block;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineTowerBlockEntity;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class PowerlineTowerBlock extends Block implements EntityBlock {
    public PowerlineTowerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return GTMPowerlinesRegistries.POWERLINE_TOWER_BLOCK_ENTITY.get().create(blockPos, blockState);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof PowerlineTowerBlockEntity blockEntity) {
            if (blockEntity.rescan()) blockEntity.attachInsulators();
        }
        super.onPlace(state, level, pos, oldState, moved);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof PowerlineTowerBlockEntity blockEntity) {
            blockEntity.detachInsulators();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
