package com.github.dewittsmith.gtm_powerlines.capabilities;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;

public class PowerlineInsulatorEnergy implements IEnergyContainer {

    private final PowerlineInsulatorBlockEntity blockEntity;

    public PowerlineInsulatorEnergy(PowerlineInsulatorBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    private Direction mountedSide() {
        return blockEntity.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION).getOpposite();
    }

    @Override
    public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
        if (side != mountedSide()) return 0;
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) return 0;
        return PowerlineNetwork.get(level).sendEnergy(blockEntity.getBlockPos(), voltage, amperage);
    }

    @Override
    public boolean inputsEnergy(Direction side) {
        return side == mountedSide();
    }

    @Override
    public boolean outputsEnergy(Direction side) {
        return side == mountedSide();
    }

    @Override
    public long changeEnergy(long differenceAmount) {
        return 0;
    }

    @Override
    public long getEnergyStored() {
        return 0;
    }

    @Override
    public long getEnergyCapacity() {
        return 0;
    }

    @Override
    public long getInputAmperage() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getInputVoltage() {
        return Long.MAX_VALUE;
    }
}
