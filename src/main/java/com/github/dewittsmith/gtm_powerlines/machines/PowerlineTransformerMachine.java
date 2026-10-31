package com.github.dewittsmith.gtm_powerlines.machines;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.wire.WireConnectionLimit;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;

import static com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries.POWERLINE_INSULATOR;

public class PowerlineTransformerMachine extends WorkableMultiblockMachine implements WireConnectionLimit {
    private Iterable<BlockPos> insulators = Collections.emptyList();

    public PowerlineTransformerMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();

        insulators = getMultiblockState().getMatchContext().getOrDefault("insulators", Collections.emptyList());
        setInsulatorsConnected(insulators, true);
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();

        setInsulatorsConnected(insulators, false);
        insulators = Collections.emptyList();
    }

    private void setInsulatorsConnected(Iterable<BlockPos> positions, boolean connected) {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(POWERLINE_INSULATOR.get())) continue;

            if (level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity be) {
                be.setController(connected ? getPos() : null);
            }
        }
    }

    @Override
    public int getMaxConnections() {
        return 1;
    }
}
