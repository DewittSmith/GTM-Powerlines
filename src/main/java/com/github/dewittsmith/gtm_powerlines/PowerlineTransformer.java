package com.github.dewittsmith.gtm_powerlines;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import static com.github.dewittsmith.gtm_powerlines.GTMPowerlinesMachines.POWERLINE_INSULATOR;

public class PowerlineTransformer extends WorkableMultiblockMachine {
    private static final Set<PowerlineTransformer> FORMED = Collections.newSetFromMap(new WeakHashMap<>());

    private Iterable<BlockPos> insulators = Collections.emptyList();
    private LongSet structurePositions = LongSets.emptySet();

    public PowerlineTransformer(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        insulators = getMultiblockState().getMatchContext().getOrDefault("insulators", Collections.emptyList());
        setInsulatorsConnected(insulators, true);

        structurePositions = getMultiblockState().cache;
        FORMED.add(this);
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();

        FORMED.remove(this);
        structurePositions = LongSets.emptySet();

        setInsulatorsConnected(insulators, false);
        insulators = Collections.emptyList();
    }

    private void setInsulatorsConnected(Iterable<BlockPos> positions, boolean connected) {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.is(POWERLINE_INSULATOR.get())
                    && state.getValue(PowerlineInsulator.IS_MULTIBLOCK_PART) != connected) {
                level.setBlock(pos, state.setValue(PowerlineInsulator.IS_MULTIBLOCK_PART, connected), 3);
            }
        }
    }

    public boolean containsPart(BlockPos pos) {
        return structurePositions.contains(pos.asLong());
    }

    public static Optional<PowerlineTransformer> findByPart(Level level, BlockPos pos) {
        return FORMED.stream()
                .filter(m -> m.getLevel() == level && m.containsPart(pos))
                .findFirst();
    }
}
