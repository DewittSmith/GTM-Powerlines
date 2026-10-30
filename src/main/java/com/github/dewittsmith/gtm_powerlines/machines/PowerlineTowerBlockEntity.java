package com.github.dewittsmith.gtm_powerlines.machines;

import com.github.dewittsmith.gtm_powerlines.ConfigHolder;
import com.github.dewittsmith.gtm_powerlines.GTMPowerlinesMachines;
import com.github.dewittsmith.gtm_powerlines.trackers.PowerlineTowerTracker;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PowerlineTowerBlockEntity extends BlockEntity {
    private static final int MAX_BLOCKS = 64;

    private final LongSet structureBlocks = new LongOpenHashSet();
    private final LongSet insulatorBlocks = new LongOpenHashSet();
    private int baseElevation = 0;

    public PowerlineTowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public LongSet getStructureBlocks() {
        return LongSets.unmodifiable(structureBlocks);
    }

    public LongSet getInsulatorBlocks() {
        return LongSets.unmodifiable(insulatorBlocks);
    }

    private static boolean isStructure(BlockState s) {
        for (TagKey<Block> tag : ConfigHolder.INSTANCE.towerTags()) {
            if (s.is(tag)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isInsulator(BlockState s) {
        return s.is(GTMPowerlinesMachines.POWERLINE_INSULATOR.get());
    }

    public void attachInsulators() {
        setInsulatorsConnected(insulatorBlocks, true);
    }

    public void detachInsulators() {
        setInsulatorsConnected(insulatorBlocks, false);
    }

    private void setInsulatorsConnected(LongSet positions, boolean connected) {
        if (level == null || level.isClientSide) return;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Long current : positions) {
            cursor.set(BlockPos.getX(current), BlockPos.getY(current), BlockPos.getZ(current));

            if (level.getBlockEntity(cursor) instanceof PowerlineInsulatorBlockEntity insulator) {
                insulator.setController(connected ? worldPosition : null);
            }
        }
    }

    public boolean rescan() {
        if (level == null || level.isClientSide) return false;

        LongSet found = new LongOpenHashSet();
        LongSet insulators = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

        queue.enqueue(worldPosition.asLong());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        var minElevation = worldPosition.getY();
        while (!queue.isEmpty()) {
            long current = queue.dequeueLong();
            if (!found.add(current)) continue;

            for (Direction direction : Direction.values()) {
                cursor.set(BlockPos.getX(current), BlockPos.getY(current), BlockPos.getZ(current)).move(direction);
                long key = cursor.asLong();
                if (found.contains(key)) continue;

                BlockState state = level.getBlockState(cursor);
                if (state.isAir()) continue;

                if (isStructure(state)) {
                    queue.enqueue(key);
                    if (cursor.getY() < minElevation) minElevation = cursor.getY();
                } else if (isInsulator(state)) {
                    queue.enqueue(key);
                    insulators.add(key);
                    if (cursor.getY() < minElevation) minElevation = cursor.getY();
                }

                if (found.size() > MAX_BLOCKS) return false;
            }
        }

        structureBlocks.clear();
        structureBlocks.addAll(found);

        detachInsulators();
        insulatorBlocks.clear();
        insulatorBlocks.addAll(insulators);
        attachInsulators();

        baseElevation = minElevation;

        setChanged();
        PowerlineTowerTracker.update(level, worldPosition, structureBlocks);
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLongArray("structureBlocks", structureBlocks.toLongArray());
        tag.putLongArray("insulatorBlocks", insulatorBlocks.toLongArray());
        tag.putInt("baseElevation", baseElevation);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        structureBlocks.clear();
        structureBlocks.addAll(new LongOpenHashSet(tag.getLongArray("structureBlocks")));

        insulatorBlocks.clear();
        insulatorBlocks.addAll(new LongOpenHashSet(tag.getLongArray("insulatorBlocks")));

        baseElevation = tag.getInt("baseElevation");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            PowerlineTowerTracker.update(level, worldPosition, structureBlocks);
            rescan();
        }
    }
}
