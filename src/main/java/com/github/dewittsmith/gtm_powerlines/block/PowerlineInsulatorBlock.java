package com.github.dewittsmith.gtm_powerlines.block;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    public static DirectionProperty DIRECTION = DirectionProperty.create("direction");

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

        if (canSupportCenter(level, supportPos, facing)) {
            return defaultBlockState().setValue(DIRECTION, facing);
        }

        return null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var facing = state.getValue(DIRECTION);
        var supportPos = pos.relative(facing.getOpposite());
        return canSupportCenter(level, supportPos, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return dir == state.getValue(DIRECTION).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, neighbor, level, pos, neighborPos);
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
        if (!state.is(newState.getBlock())
                && !level.isClientSide
                && level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity blockEntity) {

            var direction = state.getValue(DIRECTION);
            for (var edge : blockEntity.getConnections()) {
                var item = ForgeRegistries.ITEMS.getValue(edge.wire());
                if (item == null) continue;
                Block.popResourceFromFace(level, pos, direction, new ItemStack(item, edge.wireCount()));
            }
            blockEntity.disconnectAll();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
