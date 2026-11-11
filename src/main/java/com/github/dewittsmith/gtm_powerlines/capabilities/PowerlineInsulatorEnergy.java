package com.github.dewittsmith.gtm_powerlines.capabilities;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;

public class PowerlineInsulatorEnergy implements IEnergyContainer {

    private final PowerlineInsulatorBlockEntity blockEntity;
    // per-handler re-entrancy guard, same as GTCEu's EnergyNetHandler
    private boolean transfer;

    public PowerlineInsulatorEnergy(PowerlineInsulatorBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    private Direction mountedSide() {
        return blockEntity.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION).getOpposite();
    }

    @Override
    public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
        if (transfer || voltage <= 0 || amperage <= 0) return 0;
        if (side != null && side != mountedSide()) return 0;
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) return 0;

        transfer = true;
        try {
            return PowerlineNetwork.get(level).acceptEnergy(blockEntity.getBlockPos(), voltage, amperage);
        } finally {
            transfer = false;
        }
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
    public long getEnergyCanBeInserted() {
        return getEnergyCapacity();
    }

    @Override
    public long getEnergyCapacity() {
        return getInputVoltage() * getInputAmperage();
    }

    @Override
    public long getInputAmperage() {
        var props = blockEntity.getWireProperties();
        return props == null ? 0 : props.getAmperage();
    }

    @Override
    public long getInputVoltage() {
        var props = blockEntity.getWireProperties();
        return props == null ? 0 : props.getVoltage();
    }

    @Override
    public boolean isOneProbeHidden() {
        return true;
    }
}
