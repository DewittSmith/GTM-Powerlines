package com.github.dewittsmith.gtm_powerlines.wire;

import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
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
        return Math.max(2, Mth.ceil(length));
    }

    public static Vec3 sample(Vec3 a, Vec3 b, double t, double sag) {
        return new Vec3(
                Mth.lerp(t, a.x, b.x),
                Mth.lerp(t, a.y, b.y) - sag * 4.0 * t * (1.0 - t),
                Mth.lerp(t, a.z, b.z));
    }

    public static Vec3 sampleTangent(Vec3 a, Vec3 b, double t, double sag) {
        return new Vec3(
                b.x - a.x,
                b.y - a.y - 4.0 * sag * (1.0 - 2.0 * t),
                b.z - a.z
        ).normalize();
    }

    public static Vec3 getAttachmentPoint(BlockEntity be) {
        final float OFFSET = 5f / 16f;

        if (be == null) return Vec3.ZERO;
        var pos = be.getBlockPos().getCenter();
        var dir = be.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION);
        return pos.add(dir.getStepX() * OFFSET, dir.getStepY() * OFFSET, dir.getStepZ() * OFFSET);
    }

    public static Vec3 getAttachmentPoint(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return pos.getCenter();
        var be = level.getBlockEntity(pos);
        return WireGeometry.getAttachmentPoint(be);
    }

    public static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        double abX = b.x - a.x;
        double abY = b.y - a.y;
        double abZ = b.z - a.z;

        double apX = p.x - a.x;
        double apY = p.y - a.y;
        double apZ = p.z - a.z;

        double abLenSq = abX * abX + abY * abY + abZ * abZ;
        if (abLenSq == 0) return Math.sqrt(apX * apX + apY * apY + apZ * apZ);

        double t = Mth.clamp((apX * abX + apY * abY + apZ * abZ) / abLenSq, 0, 1);

        double closestX = a.x + t * abX;
        double closestY = a.y + t * abY;
        double closestZ = a.z + t * abZ;

        double diffX = p.x - closestX;
        double diffY = p.y - closestY;
        double diffZ = p.z - closestZ;

        return Math.sqrt(diffX * diffX + diffY * diffY + diffZ * diffZ);
    }
}