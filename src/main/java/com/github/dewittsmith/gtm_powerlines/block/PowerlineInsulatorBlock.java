package com.github.dewittsmith.gtm_powerlines.block;

import com.gregtechceu.gtceu.common.block.CableBlock;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PowerlineInsulatorBlock extends Block implements EntityBlock {

    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.UP, box(5, 0, 5, 11, 16, 11),
            Direction.DOWN, box(5, 0, 5, 11, 16, 11),
            Direction.NORTH, box(5, 5, 0, 11, 11, 16),
            Direction.SOUTH, box(5, 5, 0, 11, 11, 16),
            Direction.WEST, box(0, 5, 5, 16, 11, 11),
            Direction.EAST, box(0, 5, 5, 16, 11, 11));

    /** Ticks to wait before checking the supporting cable's connection (lets GT finish swapping cables). */
    private static final int CONNECTION_CHECK_DELAY = 2;

    public static final DirectionProperty DIRECTION = DirectionProperty.create("direction");

    public PowerlineInsulatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(DIRECTION, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRECTION);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(DIRECTION));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var level = ctx.getLevel();
        var facing = ctx.getClickedFace();
        var supportPos = ctx.getClickedPos().relative(facing.getOpposite());

        if (canAttachTo(level, supportPos, facing)) {
            return defaultBlockState().setValue(DIRECTION, facing);
        }

        return null;
    }

    /**
     * Like GT machines: placing an insulator against a cable opens the cable on that side.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        var facing = state.getValue(DIRECTION);
        if (level.getBlockEntity(pos.relative(facing.getOpposite())) instanceof CableBlockEntity cable) {
            cable.setConnection(facing, true, false);
        }
    }

    /** Structural check only: a sturdy face or any GT cable. Cable connections are checked in {@link #tick}. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var facing = state.getValue(DIRECTION);
        var supportPos = pos.relative(facing.getOpposite());
        return canAttachTo(level, supportPos, facing);
    }

    private boolean canAttachTo(LevelReader level, BlockPos supportPos, Direction facing) {
        if (canSupportCenter(level, supportPos, facing)) return true;
        return level.getBlockEntity(supportPos) instanceof CableBlockEntity;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir != state.getValue(DIRECTION).getOpposite()) {
            return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
        }

        if (!state.canSurvive(level, pos)) {
            // the cable we hang on melted: our wires burn with it instead of dropping
            if (neighbor.getBlock() instanceof BaseFireBlock &&
                    level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity insulator) {
                insulator.markBurned();
            }
            return Blocks.AIR.defaultBlockState();
        }

        // a GT cable may have been swapped (e.g. its insulation burned off); re-check the connection a bit later,
        // once the new cable restored its sides
        if (neighbor.getBlock() instanceof CableBlock) level.scheduleTick(pos, this, CONNECTION_CHECK_DELAY);
        return state;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
                                boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && neighborPos.equals(pos.relative(state.getValue(DIRECTION).getOpposite()))) {
            level.scheduleTick(pos, this, CONNECTION_CHECK_DELAY);
        }
    }

    /** Breaks the insulator when the cable it hangs on is no longer connected on its side. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var facing = state.getValue(DIRECTION);
        if (level.getBlockEntity(pos.relative(facing.getOpposite())) instanceof CableBlockEntity cable &&
                !cable.isConnected(facing)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(DIRECTION, rot.rotate(state.getValue(DIRECTION)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(DIRECTION, mirror.mirror(state.getValue(DIRECTION)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return GTMPowerlinesRegistries.POWERLINE_INSULATOR_BLOCK_ENTITY.get().create(blockPos, blockState);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide &&
                level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity blockEntity) {

            var direction = state.getValue(DIRECTION);
            if (!blockEntity.isBurned()) {
                for (var edge : blockEntity.getConnections()) {
                    var item = ForgeRegistries.ITEMS.getValue(edge.wire());
                    if (item == null) continue;
                    Block.popResourceFromFace(level, pos, direction, new ItemStack(item, edge.wireCount()));
                }
            }
            if (level instanceof ServerLevel serverLevel) {
                var network = PowerlineNetwork.get(serverLevel);
                if (blockEntity.isBurned()) network.burnAll(pos);
                else blockEntity.disconnectAll();
                network.setTransformerNode(pos, null);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
