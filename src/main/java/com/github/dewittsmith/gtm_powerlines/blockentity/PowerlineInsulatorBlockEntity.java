package com.github.dewittsmith.gtm_powerlines.blockentity;

import com.github.dewittsmith.gtm_powerlines.wire.WireConnectionLimit;
import com.github.dewittsmith.gtm_powerlines.wire.WireConnectionResult;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
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
import net.minecraft.world.phys.AABB;

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

    public boolean hasConnectionCapacity() {
        if (level == null || controller == null) return false;
        var entity = level.getBlockEntity(controller);

        var maxConnections = 0;
        if (entity instanceof WireConnectionLimit machineConnectionLimit) {
            maxConnections = machineConnectionLimit.getMaxConnections();
        } else if (entity instanceof MetaMachineBlockEntity metaBe) {
            if (metaBe.metaMachine instanceof WireConnectionLimit connectionLimit) {
                maxConnections = connectionLimit.getMaxConnections();
            }
        }

        return connections.size() < maxConnections;
    }

    public WireConnectionResult connect(BlockPos target, ResourceLocation wire) {
        if (target.equals(worldPosition)) return WireConnectionResult.SAME_BLOCK;
        if (connections.stream().anyMatch(c -> c.target().equals(target)))
            return WireConnectionResult.ALREADY_CONNECTED;

        if (!hasConnectionCapacity())
            return WireConnectionResult.TOO_MANY_CONNECTIONS;

        connections.add(new Connection(target.immutable(), wire));
        sync();
        return WireConnectionResult.SUCCESS;
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
    public AABB getRenderBoundingBox() {
        AABB box = new AABB(worldPosition);

        for (var connection : connections) {
            box = box.minmax(new AABB(connection.target()));
        }

        return box.inflate(1.0);
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
