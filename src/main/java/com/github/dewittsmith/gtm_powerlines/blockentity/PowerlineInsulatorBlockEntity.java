package com.github.dewittsmith.gtm_powerlines.blockentity;

import com.gregtechceu.gtceu.api.blockentity.IPaintable;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.capabilities.PowerlineInsulatorEnergy;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.machines.PowerlineTransformerMachine;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;
import com.github.dewittsmith.gtm_powerlines.util.BlockUtils;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;
import com.github.dewittsmith.gtm_powerlines.wire.WireConnectionResult;
import com.github.dewittsmith.gtm_powerlines.wire.WireStats;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

public class PowerlineInsulatorBlockEntity extends BlockEntity implements IPaintable {

    @Nullable
    private BlockPos controller;

    /** Tint of the insulator discs, set with a GT spray can. */
    private int paintingColor = UNPAINTED_COLOR;

    /** Set when the supporting cable melted: the attached wires burn instead of dropping. Not saved. */
    private boolean burned;

    private final int maxElevation;

    private final IEnergyContainer energy = new PowerlineInsulatorEnergy(this);
    private LazyOptional<IEnergyContainer> energyCap = LazyOptional.of(() -> energy);

    public PowerlineInsulatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        maxElevation = Mth.ceil(Math.pow(GTMPowerlinesConfig.INSTANCE.wire.maxWireRange,
                1f / GTMPowerlinesConfig.INSTANCE.insulator.rangePower));
    }

    public void markBurned() {
        burned = true;
    }

    public boolean isBurned() {
        return burned;
    }

    public @Nullable BlockPos getController() {
        return controller;
    }

    public void setController(@Nullable BlockPos controller) {
        this.controller = controller == null ? null : controller.immutable();
        if (level instanceof ServerLevel serverLevel) {
            PowerlineNetwork.get(serverLevel).setTransformerNode(worldPosition, this.controller);
        }
        setChanged();
    }

    // region IPaintable

    @Override
    public int getPaintingColor() {
        return paintingColor;
    }

    @Override
    public void setPaintingColor(int color) {
        if (paintingColor == color) return;
        paintingColor = color;
        setChanged();
        if (level == null) return;
        if (level.isClientSide) {
            // the spray can also runs client-side: re-bake right away, the server update will then match
            requestRerender();
        } else {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public int getDefaultPaintingColor() {
        return UNPAINTED_COLOR;
    }

    // endregion

    public List<PowerlineGraph.Edge> getConnections() {
        if (level instanceof ServerLevel serverLevel) {
            return PowerlineNetwork.get(serverLevel).getGraph().atBlock(worldPosition);
        }
        return level == null ? List.of() : ClientWires.at(worldPosition);
    }

    public @Nullable WireProperties getWireProperties() {
        var connections = getConnections();
        return connections.isEmpty() ? null : connections.get(0).stats().props();
    }

    private Optional<PowerlineTransformerMachine> getTransformer() {
        return controller == null ? Optional.empty() :
                BlockUtils.instanceOf(level, controller, PowerlineTransformerMachine.class);
    }

    public int getElevation() {
        var level = getLevel();
        if (level == null) return 0;

        var cursor = worldPosition.mutable();
        for (var i = 0; i < maxElevation; ++i) {
            cursor.move(0, -1, 0);
            var bs = level.getBlockState(cursor);
            if (bs.isAir() || !bs.is(GTMPowerlinesRegistries.SURFACE)) continue;
            return worldPosition.getY() - cursor.getY();
        }

        return maxElevation;
    }

    public int connectionCount() {
        return getConnections().size();
    }

    public float maxConnectionRange() {
        if (getTransformer().isPresent()) {
            return Mth.clamp(8, GTMPowerlinesConfig.INSTANCE.wire.minWireRange,
                    GTMPowerlinesConfig.INSTANCE.wire.maxWireRange);
        }

        return Mth.clamp((float) Math.pow(getElevation(), GTMPowerlinesConfig.INSTANCE.insulator.rangePower),
                GTMPowerlinesConfig.INSTANCE.wire.minWireRange, GTMPowerlinesConfig.INSTANCE.wire.maxWireRange);
    }

    public int maxConnections() {
        if (getTransformer().isPresent()) {
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
        var transformer = getTransformer();
        if (transformer.isPresent()) return transformer.get().getTier();
        var props = getWireProperties();
        return props == null ? -1 : GTUtil.getFloorTierByVoltage(props.getVoltage());
    }

    public WireConnectionResult connect(BlockPos target, ResourceLocation wire) {
        if (!(level instanceof ServerLevel serverLevel)) return WireConnectionResult.TOO_MANY_CONNECTIONS;
        if (target.equals(worldPosition)) return WireConnectionResult.SAME_BLOCK;
        if (getConnections().stream().anyMatch(e -> e.other(worldPosition).equals(target)))
            return WireConnectionResult.ALREADY_CONNECTED;
        if (!hasConnectionCapacity()) return WireConnectionResult.TOO_MANY_CONNECTIONS;
        var other = BlockUtils.instanceOf(level, target, PowerlineInsulatorBlockEntity.class).orElse(null);
        if (other == null || !other.hasConnectionCapacity()) return WireConnectionResult.TOO_MANY_CONNECTIONS;

        var stats = WireStats.of(wire).orElse(null);
        if (stats == null) return WireConnectionResult.TOO_MANY_CONNECTIONS;
        for (var tier : new int[] { getWireTier(), other.getWireTier() }) {
            if (tier != -1 && tier != stats.tier()) return WireConnectionResult.incompatibleTier(tier);
        }

        var startDir = getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION);
        var targetDir = other.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION);

        var edge = PowerlineNetwork.get(serverLevel).connect(worldPosition, startDir, target, targetDir, wire);
        return edge != null ? WireConnectionResult.success(edge) : WireConnectionResult.ALREADY_CONNECTED;
    }

    public boolean disconnectFrom(BlockPos target) {
        return level instanceof ServerLevel serverLevel &&
                PowerlineNetwork.get(serverLevel).disconnect(worldPosition, target);
    }

    public List<PowerlineGraph.Edge> disconnectAll() {
        return level instanceof ServerLevel serverLevel ?
                PowerlineNetwork.get(serverLevel).disconnectAll(worldPosition) :
                List.of();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (controller != null) tag.put("controller", NbtUtils.writeBlockPos(controller));
        if (isPainted()) tag.putInt("paint", paintingColor);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        controller = tag.contains("controller") ? NbtUtils.readBlockPos(tag.getCompound("controller")) : null;
        var oldPaint = paintingColor;
        paintingColor = tag.contains("paint", Tag.TAG_INT) ? tag.getInt("paint") : UNPAINTED_COLOR;
        if (oldPaint != paintingColor && level != null && level.isClientSide) requestRerender();
    }

    /** Re-bakes the chunk section so the disc tint (a block colour) updates. Client only. */
    private void requestRerender() {
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        var tag = super.getUpdateTag();
        if (controller != null) tag.put("controller", NbtUtils.writeBlockPos(controller));
        if (isPainted()) tag.putInt("paint", paintingColor);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        var tag = pkt.getTag();
        load(tag == null ? new CompoundTag() : tag);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        var attachSide = getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION).getOpposite();
        if (cap == GTCapability.CAPABILITY_ENERGY_CONTAINER && (side == null || side == attachSide)) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> energy);
    }
}
