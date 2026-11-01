package com.github.dewittsmith.gtm_powerlines.blockentity;

import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.network.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.util.BlockUtils;
import com.github.dewittsmith.gtm_powerlines.wire.IWireMachine;
import com.github.dewittsmith.gtm_powerlines.wire.WireConnectionResult;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

public class PowerlineInsulatorBlockEntity extends BlockEntity {
    @Getter
    @Nullable
    private BlockPos controller;

    public PowerlineInsulatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
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

    public int connectionCount() {
        return getConnections().size();
    }

    public float maxConnectionRange() {
        if (controller == null) return 0f;
        var wireMachine = BlockUtils.instanceOf(level, controller, IWireMachine.class);
        var wireDistance = wireMachine.isEmpty() ? 0f : wireMachine.get().getWireDistanceFor(worldPosition);
        return Mth.clamp(wireDistance, GTMPowerlinesConfig.INSTANCE.minWireRange, GTMPowerlinesConfig.INSTANCE.maxWireRange);
    }

    public boolean hasConnectionCapacity() {
        if (!isServer() || controller == null) return false;
        var wireMachine = BlockUtils.instanceOf(level, controller, IWireMachine.class);
        var maxConnections = wireMachine.isEmpty() ? 0 : wireMachine.get().getMaxConnections();
        return connectionCount() < maxConnections;
    }

    public WireConnectionResult connect(BlockPos target, ResourceLocation wire) {
        if (!isServer()) return WireConnectionResult.SAME_BLOCK;
        if (target.equals(worldPosition)) return WireConnectionResult.SAME_BLOCK;
        if (getConnections().stream().anyMatch(e -> e.other(worldPosition).equals(target)))
            return WireConnectionResult.ALREADY_CONNECTED;
        if (!hasConnectionCapacity()) return WireConnectionResult.TOO_MANY_CONNECTIONS;

        return network().connect((ServerLevel) level, worldPosition, target, wire)
                ? WireConnectionResult.SUCCESS
                : WireConnectionResult.ALREADY_CONNECTED;
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