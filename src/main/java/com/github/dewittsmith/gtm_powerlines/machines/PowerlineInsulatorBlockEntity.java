package com.github.dewittsmith.gtm_powerlines.machines;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PowerlineInsulatorBlockEntity extends BlockEntity {
    public record Connection(BlockPos target, ResourceLocation wire) {
    }

    @Getter
    @Nullable
    private BlockPos controller;
    private final List<Connection> connections = new ArrayList<>();

    public PowerlineInsulatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public List<Connection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    public void setController(BlockPos controller) {
        this.controller = controller == null ? null : controller.immutable();
        sync();
    }

    public boolean connect(BlockPos target, ResourceLocation wire) {
        if (target.equals(worldPosition)) return false;
        if (connections.stream().anyMatch(c -> c.target().equals(target))) return false;
        connections.add(new Connection(target.immutable(), wire));
        sync();
        return true;
    }

    public void removeConnectionsTo(BlockPos target) {
        if (connections.removeIf(c -> c.target().equals(target))) sync();
    }

    public void disconnectAll() {
        if (level == null || level.isClientSide) return;
        for (Connection c : connections) {
            if (level.isLoaded(c.target())
                    && level.getBlockEntity(c.target()) instanceof PowerlineInsulatorBlockEntity other) {
                other.removeConnectionsTo(worldPosition);
            }
        }
        connections.clear();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (Connection c : connections) {
            CompoundTag t = new CompoundTag();
            t.put("target", NbtUtils.writeBlockPos(c.target()));
            t.putString("wire", c.wire().toString());
            list.add(t);
        }

        if (controller != null) tag.put("controller", NbtUtils.writeBlockPos(controller));
        tag.put("connections", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        connections.clear();

        controller = tag.contains("controller") ? NbtUtils.readBlockPos(tag.getCompound("controller")) : null;
        for (Tag raw : tag.getList("connections", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            connections.add(new Connection(
                    NbtUtils.readBlockPos(t.getCompound("target")),
                    new ResourceLocation(t.getString("wire"))));
        }
    }


    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
