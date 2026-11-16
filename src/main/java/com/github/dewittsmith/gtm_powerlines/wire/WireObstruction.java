package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

public final class WireObstruction {

    private static final double STEP = 0.125;

    public static boolean enabled() {
        return GTMPowerlinesConfig.INSTANCE.wire.checkObstructions;
    }

    public static List<BlockPos> find(LevelReader level, Vec3 start, Vec3 end) {
        var result = new LinkedHashSet<BlockPos>();
        var clear = new HashSet<BlockPos>();
        for (var point : samples(start, end)) {
            var pos = BlockPos.containing(point);
            if (result.contains(pos)) continue;
            if (!level.hasChunkAt(pos)) continue;

            var state = level.getBlockState(pos);
            if (clear.contains(pos) && !hasPartialShape(state, level, pos)) continue;
            if (ignored(state)) {
                clear.add(pos);
                continue;
            }
            if (contains(state.getCollisionShape(level, pos), pos, point)) result.add(pos);
            else clear.add(pos);
        }
        return new ArrayList<>(result);
    }

    public static List<BlockPos> find(LevelReader level, PowerlineGraph.Edge edge) {
        return find(level, WireGeometry.getAttachmentPoint(edge.a(), edge.dirA(), edge.stats()),
                WireGeometry.getAttachmentPoint(edge.b(), edge.dirB(), edge.stats()));
    }

    public static boolean blocks(LevelReader level, PowerlineGraph.Edge edge, BlockPos pos, BlockState state) {
        if (ignored(state) || edge.a().equals(pos) || edge.b().equals(pos)) return false;
        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;

        var start = WireGeometry.getAttachmentPoint(edge.a(), edge.dirA(), edge.stats());
        var end = WireGeometry.getAttachmentPoint(edge.b(), edge.dirB(), edge.stats());
        var bounds = new AABB(start, end).inflate(0, WireGeometry.sag(start, end), 0).inflate(0.1);
        if (!bounds.intersects(new AABB(pos))) return false;

        for (var point : samples(start, end)) {
            if (contains(shape, pos, point)) return true;
        }
        return false;
    }

    public static boolean ignored(BlockState state) {
        return state.isAir() || state.is(GTMPowerlinesRegistries.WIRE_PASSTHROUGH) ||
                state.getBlock() instanceof PowerlineInsulatorBlock;
    }

    private static boolean hasPartialShape(BlockState state, LevelReader level, BlockPos pos) {
        return !ignored(state) && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean contains(VoxelShape shape, BlockPos pos, Vec3 point) {
        if (shape.isEmpty()) return false;
        var local = point.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (var box : shape.toAabbs()) {
            if (local.x >= box.minX && local.x < box.maxX && local.y >= box.minY && local.y < box.maxY &&
                    local.z >= box.minZ && local.z < box.maxZ) {
                return true;
            }
        }
        return false;
    }

    private static List<Vec3> samples(Vec3 start, Vec3 end) {
        double sag = WireGeometry.sag(start, end);
        int n = Math.max(2, Mth.ceil(WireGeometry.length(start, end, sag) / STEP) + 1);
        var points = new ArrayList<Vec3>(n);
        for (int i = 0; i < n; i++) points.add(WireGeometry.sample(start, end, i / (n - 1.0), sag));
        return points;
    }
}
