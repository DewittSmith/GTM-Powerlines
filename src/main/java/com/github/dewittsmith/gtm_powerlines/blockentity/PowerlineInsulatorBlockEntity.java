package com.github.dewittsmith.gtm_powerlines.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.machines.PowerlineTransformerMachine;
import com.github.dewittsmith.gtm_powerlines.network.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;
import com.github.dewittsmith.gtm_powerlines.util.BlockUtils;
import com.github.dewittsmith.gtm_powerlines.wire.WireConnectionResult;
import com.github.dewittsmith.gtm_powerlines.wire.WireInfo;
import lombok.Getter;

import java.util.List;

import javax.annotation.Nullable;

public class PowerlineInsulatorBlockEntity extends BlockEntity {

    @Getter
    @Nullable
    private BlockPos controller;

    private final int maxElevation;

    public PowerlineInsulatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        maxElevation = Mth.ceil(Math.pow(GTMPowerlinesConfig.INSTANCE.wire.maxWireRange,
                1f / GTMPowerlinesConfig.INSTANCE.insulator.rangePower));
    }

    public void setController(@Nullable BlockPos controller) {
        this.controller = controller == null ? null : controller.immutable();
        setChanged();
    }

    private PowerlineNetwork network() {
        return PowerlineNetwork.get((ServerLevel) level);
    }

    private boolean isServer() {
        return level instanceof ServerLevel;
    }

    public List<PowerlineNetwork.Edge> getConnections() {
        return isServer() ? network().at(worldPosition) : List.of();
    }

    public int getElevation() {
        var level = getLevel();
        if (level == null) return 0;

        var cursor = worldPosition.mutable();
        for (var i = 0; i < maxElevation; ++i) {
            cursor.move(0, -1, 0);
            var bs = level.getBlockState(cursor);
            if (bs.isAir() || bs.is(GTMPowerlinesRegistries.INSULATOR_SURFACE_IGNORE)) continue;
            return worldPosition.getY() - cursor.getY();
        }

        return maxElevation;
    }

    public int connectionCount() {
        return getConnections().size();
    }

    public float maxConnectionRange() {
        if (controller != null &&
                BlockUtils.instanceOf(level, controller, PowerlineTransformerMachine.class).isPresent()) {
            return Mth.clamp(8, GTMPowerlinesConfig.INSTANCE.wire.minWireRange,
                    GTMPowerlinesConfig.INSTANCE.wire.maxWireRange);
        }

        return Mth.clamp((float) Math.pow(getElevation(), GTMPowerlinesConfig.INSTANCE.insulator.rangePower),
                GTMPowerlinesConfig.INSTANCE.wire.minWireRange, GTMPowerlinesConfig.INSTANCE.wire.maxWireRange);
    }

    public int maxConnections() {
        if (controller != null &&
                BlockUtils.instanceOf(level, controller, PowerlineTransformerMachine.class).isPresent()) {
            return 1;
        }

        if (GTMPowerlinesConfig.INSTANCE.insulator.maxWireConnections < 2) {
            return Integer.MAX_VALUE;
        }

        return GTMPowerlinesConfig.INSTANCE.insulator.maxWireConnections;
    }

    public boolean hasConnectionCapacity() {
        return connectionCount() < maxConnections();
    }

    public int getWireTier() {
        if (controller == null) return -1;
        var transformer = BlockUtils.instanceOf(level, controller, PowerlineTransformerMachine.class);
        if (transformer.isPresent()) return transformer.get().getTier();
        var connections = getConnections();
        if (connections.isEmpty()) return -1;
        var wireInfoOpt = WireInfo.of(connections.get(0).wire());
        return wireInfoOpt.map(WireInfo::tier).orElse(-1);
    }

    public WireConnectionResult connect(BlockPos target, ResourceLocation wire) {
        if (!isServer()) return WireConnectionResult.TOO_MANY_CONNECTIONS;
        if (target.equals(worldPosition)) return WireConnectionResult.SAME_BLOCK;
        if (getConnections().stream().anyMatch(e -> e.other(worldPosition).equals(target)))
            return WireConnectionResult.ALREADY_CONNECTED;
        if (!hasConnectionCapacity()) return WireConnectionResult.TOO_MANY_CONNECTIONS;
        var other = BlockUtils.instanceOf(level, target, PowerlineInsulatorBlockEntity.class);
        if (other.isEmpty() || !other.get().hasConnectionCapacity()) return WireConnectionResult.TOO_MANY_CONNECTIONS;

        var tier = getWireTier();
        var wireInfoOpt = WireInfo.of(wire);
        if (wireInfoOpt.isEmpty()) return WireConnectionResult.TOO_MANY_CONNECTIONS;
        if (tier != -1 && tier != wireInfoOpt.get().tier()) return WireConnectionResult.incompatibleTier(tier);

        return network().connect((ServerLevel) level, worldPosition, target, wire) ? WireConnectionResult.SUCCESS :
                WireConnectionResult.ALREADY_CONNECTED;
    }

    public boolean disconnectFrom(BlockPos target) {
        return isServer() && network().disconnect((ServerLevel) level, worldPosition, target);
    }

    public List<PowerlineNetwork.Edge> disconnectAll() {
        return isServer() ? network().disconnectAll((ServerLevel) level, worldPosition) : List.of();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (controller != null) tag.put("controller", NbtUtils.writeBlockPos(controller));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        controller = tag.contains("controller") ? NbtUtils.readBlockPos(tag.getCompound("controller")) : null;
    }
}
