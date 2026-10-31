package com.github.dewittsmith.gtm_powerlines;

import com.github.dewittsmith.gtm_powerlines.machines.PowerlineInsulatorBlockEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public final class WireTargeting {
    public record Hit(PowerlineInsulatorBlockEntity.Connection connection, Vec3 point) {
    }

    private static final double MAX_DISTANCE = 0.35;   // how close the ray must pass to a wire
    private static final double REACH = 12.0;

    public static @Nullable Hit find(PowerlineInsulatorBlockEntity be, Vec3 eye, Vec3 look) {
        var level = be.getLevel();
        if (level == null) return null;

        Vec3 from = WireGeometry.getAttachmentPoint(be);
        Hit best = null;
        double bestDist = MAX_DISTANCE;

        for (var c : be.getConnections()) {
            Vec3 to = WireGeometry.getAttachmentPoint(level.getBlockEntity(c.target()));
            double length = from.distanceTo(to);
            double sag = WireGeometry.sag(length);
            int n = WireGeometry.samples(length);

            Vec3 prev = from;
            for (int i = 1; i < n; i++) {
                Vec3 next = WireGeometry.sample(from, to, i / (n - 1.0), sag);
                Vec3[] closest = new Vec3[1];
                double d = rayToSegment(eye, look, prev, next, closest);
                if (d < bestDist) {
                    bestDist = d;
                    best = new Hit(c, closest[0]);
                }
                prev = next;
            }
        }
        return best;
    }

    /**
     * Distance between a ray (o + s*u, s in [0, REACH]) and the segment p-q. Writes the point on the segment.
     */
    private static double rayToSegment(Vec3 o, Vec3 u, Vec3 p, Vec3 q, Vec3[] outPoint) {
        Vec3 v = q.subtract(p), w = o.subtract(p);
        double b = u.dot(v), c = v.dot(v), d = u.dot(w), e = v.dot(w);
        if (c < 1e-9) return Double.MAX_VALUE;

        double denom = c - b * b;
        double t = denom < 1e-9 ? 0 : Mth.clamp((e - b * d) / denom, 0, 1);
        double s = Mth.clamp(t * b - d, 0, REACH);
        t = Mth.clamp((e + s * b) / c, 0, 1);

        Vec3 onRay = o.add(u.scale(s));
        Vec3 onSeg = p.add(v.scale(t));
        outPoint[0] = onSeg;
        return onRay.distanceTo(onSeg);
    }
}