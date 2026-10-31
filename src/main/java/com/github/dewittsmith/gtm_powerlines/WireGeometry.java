package com.github.dewittsmith.gtm_powerlines;

import com.github.dewittsmith.gtm_powerlines.machines.PowerlineInsulator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class WireGeometry {
    public static double sag(double length) {
        return Math.min(length * 0.04, 2.0);
    }

    public static int samples(double length) {
        return Math.max(8, (int) (length * 2));
    }

    public static Vec3 sample(Vec3 a, Vec3 b, double t, double sag) {
        return new Vec3(
                Mth.lerp(t, a.x, b.x),
                Mth.lerp(t, a.y, b.y) - sag * 4.0 * t * (1.0 - t),
                Mth.lerp(t, a.z, b.z));
    }

    public static Vec3 getAttachmentPoint(BlockEntity be) {
        final float OFFSET = 6.5f / 16f;

        if (be == null) return Vec3.ZERO;
        var pos = be.getBlockPos().getCenter();
        var dir = be.getBlockState().getValue(PowerlineInsulator.DIRECTION);
        return pos.add(dir.getStepX() * OFFSET, dir.getStepY() * OFFSET, dir.getStepZ() * OFFSET);
    }

    public static Vec3 getAttachmentPoint(Level level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        return WireGeometry.getAttachmentPoint(be);
    }
}