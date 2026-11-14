package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;

public final class WireGeometry {

    private static final float INDENT = 0.01f;

    private static final float[] WIRE_SIZES = {
            1, 2, 2, 3, 4,
            1, 2, 2, 3, 4
    };

    public static float pixelSize(WireStats stats) {
        return WIRE_SIZES[stats.insulation().ordinal()] - INDENT;
    }

    public static double sag(Vec3 a, Vec3 b) {
        return Math.min(a.distanceTo(b) * 0.06, 2.0);
    }

    public static double length(Vec3 a, Vec3 b, double sag) {
        var dx = b.x - a.x;
        var dy = b.y - a.y;
        var dz = b.z - a.z;

        var d2 = dx * dx + dy * dy + dz * dz;
        if (d2 < 1e-4) return 0f;

        var d = Math.sqrt(d2);
        var horizontalD2 = dx * dx + dz * dz;

        // Second degree approximation: d + (8s^2(dx^2 + dz^2) / 3d^3)
        return d + (8.0 * sag * sag * horizontalD2) / (3.0 * d2 * d);
    }

    public static float samples(double length) {
        return (float) Math.max(1, length / (14.0 / 16.0));
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
                b.z - a.z).normalize();
    }

    public static Vec3 getAttachmentPoint(BlockEntity be, WireStats wire) {
        if (be == null) return Vec3.ZERO;
        var dir = be.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION);
        return getAttachmentPoint(be.getBlockPos(), dir, wire);
    }

    public static Vec3 getAttachmentPoint(Level level, BlockPos pos, WireStats wire) {
        if (!level.isLoaded(pos)) return pos.getCenter();
        var be = level.getBlockEntity(pos);
        return getAttachmentPoint(be, wire);
    }

    public static Vec3 getAttachmentPoint(BlockPos pos, Direction direction, WireStats wire) {
        float offset = (6f - pixelSize(wire) / 2) / 16f;
        return pos.getCenter().add(direction.getStepX() * offset, direction.getStepY() * offset,
                direction.getStepZ() * offset);
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
