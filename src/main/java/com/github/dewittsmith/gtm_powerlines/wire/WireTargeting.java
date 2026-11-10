package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;

import java.util.Collection;

import javax.annotation.Nullable;

public final class WireTargeting {

    public record Hit(PowerlineGraph.Edge edge, Vec3 point) {}

    private static final double MAX_DISTANCE = 0.35;
    private static final double REACH = 12.0;

    public static @Nullable Hit find(Level level, BlockPos origin, Collection<PowerlineGraph.Edge> edges, Vec3 eye,
                                     Vec3 look) {
        Vec3 from = WireGeometry.getAttachmentPoint(level, origin);
        Hit best = null;
        double bestDist = MAX_DISTANCE;

        for (var edge : edges) {
            var to = WireGeometry.getAttachmentPoint(level, edge.other(origin));
            double sag = WireGeometry.sag(from, to);
            double length = WireGeometry.length(from, to, sag);

            var frac = WireGeometry.samples(length);
            var n = (int) frac;
            frac -= n;

            Vec3 prev = from;
            for (int i = 1; i < n; i++) {
                Vec3 next = WireGeometry.sample(from, to, i / (n - 1.0), sag);
                Vec3[] closest = new Vec3[1];
                double d = rayToSegment(eye, look, prev, next, closest);
                if (d < bestDist) {
                    bestDist = d;
                    best = new Hit(edge, closest[0]);
                }
                prev = next;
            }
        }
        return best;
    }

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
