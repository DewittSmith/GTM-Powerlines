package com.github.dewittsmith.gtm_powerlines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

public class PowerlineInsulator extends Block {
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.UP, box(5, 0, 5, 11, 16, 11),
            Direction.DOWN, box(5, 0, 5, 11, 16, 11),
            Direction.NORTH, box(5, 5, 0, 11, 11, 16),
            Direction.SOUTH, box(5, 5, 0, 11, 11, 16),
            Direction.WEST, box(0, 5, 5, 16, 11, 11),
            Direction.EAST, box(0, 5, 5, 16, 11, 11));

    public static DirectionProperty DIRECTION = DirectionProperty.create("direction");

    public PowerlineInsulator(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(DIRECTION, Direction.UP));
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
}
