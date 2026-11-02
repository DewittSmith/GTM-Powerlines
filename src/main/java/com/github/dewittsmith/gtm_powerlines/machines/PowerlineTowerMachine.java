package com.github.dewittsmith.gtm_powerlines.machines;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.TowerHighlightPacket;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;
import com.github.dewittsmith.gtm_powerlines.wire.IWireMachine;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashSet;
import java.util.Set;

public class PowerlineTowerMachine extends MultiblockControllerMachine implements IWireMachine, IInteractedMachine {
    private static final int MAX_BLOCKS = 64;

    public static final Set<PowerlineTowerMachine> RENDERED_TOWERS = new HashSet<>();

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
        for (TagKey<Block> tag : GTMPowerlinesConfig.INSTANCE.towerTags()) {
            if (s.is(tag)) {
                return true;
            }
        }

        return false;
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

    private boolean rescan() {
        var level = getLevel();
        if (level == null || level.isClientSide) return false;

        LongSet found = new LongOpenHashSet();
        LongSet insulators = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

        queue.enqueue(getPos().asLong());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

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

                if (found.size() > MAX_BLOCKS) {
                    detachInsulators();
                    insulatorBlocks.clear();
                    return false;
                }
            }
        }

        var multiblockState = getMultiblockState();

        multiblockState.clean();
        multiblockState.cache.addAll(found);

        detachInsulators();
        insulatorBlocks.clear();
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

        if (sp.isShiftKeyDown()) {
            rescan();
            Net.sendToPlayer(sp, new TowerHighlightPacket(level.dimension(), getStructureBlocks().toLongArray(), insulatorBlocks.toLongArray(), 200));
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
        return 4;
    }

    @Override
    public float getWireDistanceFor(BlockPos block) {
        return (float) Math.pow(block.getY() - baseElevation, GTMPowerlinesConfig.INSTANCE.towerRangePower);
    }
}
