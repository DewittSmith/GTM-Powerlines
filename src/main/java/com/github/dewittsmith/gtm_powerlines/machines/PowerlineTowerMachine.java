package com.github.dewittsmith.gtm_powerlines.machines;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.network.TowerHighlightPacket;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;
import com.github.dewittsmith.gtm_powerlines.wire.IWireMachine;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

public class PowerlineTowerMachine extends MultiblockControllerMachine implements IWireMachine, IInteractedMachine {

    private final LongSet insulatorBlocks = new LongOpenHashSet();
    private int baseElevation = 0;

    public PowerlineTowerMachine(IMachineBlockEntity machineBlockEntity) {
        super(machineBlockEntity);
    }

    public LongSet getStructureBlocks() {
        return LongSets.unmodifiable(getMultiblockState().cache);
    }

    public LongSet getInsulatorBlocks() {
        return LongSets.unmodifiable(insulatorBlocks);
    }

    private static boolean isStructure(BlockState s) {
        return GTMPowerlinesConfig.INSTANCE.isTowerBlock(s);
    }

    private static boolean isInsulator(BlockState s) {
        return s.is(GTMPowerlinesRegistries.POWERLINE_INSULATOR.get());
    }

    private void attachInsulators() {
        setInsulatorsConnected(insulatorBlocks, true);
    }

    private void detachInsulators() {
        setInsulatorsConnected(insulatorBlocks, false);
    }

    private void setInsulatorsConnected(LongSet positions, boolean connected) {
        var level = getLevel();
        if (level == null || level.isClientSide) return;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Long current : positions) {
            cursor.set(BlockPos.getX(current), BlockPos.getY(current), BlockPos.getZ(current));

            if (level.getBlockEntity(cursor) instanceof PowerlineInsulatorBlockEntity insulator) {
                insulator.setController(connected ? getPos() : null);
            }
        }
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (getLevel() instanceof ServerLevel level) {
            PowerlineNetwork.get(level).setTransferDirty();
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        if (getLevel() instanceof ServerLevel level) {
            PowerlineNetwork.get(level).setTransferDirty();
        }
    }

    private void invalidate() {
        if (!isFormed()) return;
        if (!(getLevel() instanceof ServerLevel level)) return;

        detachInsulators();
        insulatorBlocks.clear();
        var multiblockState = getMultiblockState();
        multiblockState.clean();

        onStructureInvalid();

        var data = MultiblockWorldSavedData.getOrCreate(level);
        data.removeAsyncLogic(this);
        data.addAsyncLogic(this);
    }

    private boolean rescan() {
        if (!(getLevel() instanceof ServerLevel level)) return false;

        detachInsulators();
        insulatorBlocks.clear();
        var multiblockState = getMultiblockState();
        multiblockState.clean();

        LongSet found = new LongOpenHashSet();
        LongSet insulators = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

        queue.enqueue(getPos().asLong());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        final var maxBlocks = GTMPowerlinesConfig.INSTANCE.tower.maxTowerBlocks;
        var minElevation = getPos().getY();
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

                if (found.size() > maxBlocks) return false;
            }
        }

        multiblockState.cache.addAll(found);
        insulatorBlocks.addAll(insulators);
        attachInsulators();

        baseElevation = minElevation;
        return true;
    }

    @Override
    public InteractionResult onUse(BlockState state, Level level, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!sp.getMainHandItem().isEmpty()) return InteractionResult.PASS;

        if (isFormed() && sp.isShiftKeyDown()) {
            if (!rescan()) {
                invalidate();
                return InteractionResult.PASS;
            }

            Net.sendToPlayer(sp, new TowerHighlightPacket(
                    level.dimension(),
                    getStructureBlocks().toLongArray(),
                    insulatorBlocks.toLongArray(),
                    getPos().asLong()));

            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean checkPattern() {
        return rescan() && !insulatorBlocks.isEmpty();
    }

    @Override
    public int getMaxConnections() {
        return GTMPowerlinesConfig.INSTANCE.tower.maxWireConnections;
    }

    @Override
    public float getWireDistanceFor(BlockPos block) {
        return Mth.clamp(
                (float) Math.pow(block.getY() - baseElevation, GTMPowerlinesConfig.INSTANCE.tower.towerRangePower),
                GTMPowerlinesConfig.INSTANCE.wire.minWireRange, GTMPowerlinesConfig.INSTANCE.wire.maxWireRange);
    }
}
