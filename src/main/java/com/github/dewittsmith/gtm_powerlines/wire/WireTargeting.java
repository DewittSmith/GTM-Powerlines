package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

public final class WireTargeting {

    private static final double TOLERANCE = 0.2;

    public static @Nullable Hit pick(PowerlineGraph graph, Player player, float partialTick) {
        double reach = player.getBlockReach();
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick).normalize();

        var hit = raycast(graph, eye, look, reach);
        if (hit == null) return null;

        var block = player.pick(reach, partialTick, false);
        if (block.getType() != HitResult.Type.MISS && eye.distanceTo(block.getLocation()) <= hit.distance()) {
            return null;
        }
        return hit;
    }

    public static @Nullable Hit raycast(PowerlineGraph graph, Vec3 eye, Vec3 look, double reach) {
        Set<PowerlineGraph.Edge> candidates = new HashSet<>();
        var reachEnd = eye.add(look.scale(reach));
        int eyeX = SectionPos.blockToSectionCoord(eye.x), eyeZ = SectionPos.blockToSectionCoord(eye.z);
        int reachX = SectionPos.blockToSectionCoord(reachEnd.x), reachZ = SectionPos.blockToSectionCoord(reachEnd.z);
        int minX = Math.min(eyeX, reachX), minZ = Math.min(eyeZ, reachZ);
        int maxX = Math.max(eyeX, reachX), maxZ = Math.max(eyeZ, reachZ);

        for (var x = minX; x <= maxX; ++x) {
            for (var z = minZ; z <= maxZ; ++z) {
                candidates.addAll(graph.atChunk(x, z));
            }
        }

        Hit best = null;
        for (var edge : candidates) {
            if (edge.stats() == null) continue;
            var hit = raycast(edge, eye, look, reach);
            if (hit != null && (best == null || hit.distance < best.distance)) best = hit;
        }
        return best;
    }

    private static @Nullable Hit raycast(PowerlineGraph.Edge edge, Vec3 eye, Vec3 look, double reach) {
        var from = WireGeometry.getAttachmentPoint(edge.a(), edge.dirA(), edge.stats());
        var to = WireGeometry.getAttachmentPoint(edge.b(), edge.dirB(), edge.stats());
        double sag = WireGeometry.sag(from, to);
        int n = Math.max(2, Mth.ceil(WireGeometry.samples(WireGeometry.length(from, to, sag))) + 1);
        double tolerance = TOLERANCE + WireGeometry.pixelSize(edge.stats()) / 32.0;

        int bestSegment = -1;
        double bestGap = Double.MAX_VALUE, bestT = 0;
        Vec3 prev = from;
        for (int i = 1; i < n; i++) {
            Vec3 next = WireGeometry.sample(from, to, i / (n - 1.0), sag);
            var result = rayToSegment(eye, look, prev, next, reach);
            if (result != null && result.gap < bestGap) {
                bestGap = result.gap;
                bestSegment = i - 1;
                bestT = result.t;
            }
            prev = next;
        }
        if (bestSegment < 0 || bestGap > tolerance) return null;

        double step = 1.0 / (n - 1);
        double lo = Math.max(0, (bestSegment - 1) * step);
        double hi = Math.min(1, (bestSegment + 2) * step);
        double u = (bestSegment + bestT) * step;
        for (int iter = 0; iter < 40; iter++) {
            double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
            if (gapToRay(eye, look, reach, WireGeometry.sample(from, to, m1, sag)) <
                    gapToRay(eye, look, reach, WireGeometry.sample(from, to, m2, sag))) {
                hi = m2;
            } else {
                lo = m1;
            }
            u = (lo + hi) / 2;
        }

        Vec3 point = WireGeometry.sample(from, to, u, sag);
        if (gapToRay(eye, look, reach, point) > tolerance) return null;
        double along = Mth.clamp(point.subtract(eye).dot(look), 0, reach);
        return new Hit(edge, point, WireGeometry.sampleTangent(from, to, u, sag), along);
    }

    private static double gapToRay(Vec3 o, Vec3 u, double reach, Vec3 p) {
        double s = Mth.clamp(p.subtract(o).dot(u), 0, reach);
        return o.add(u.scale(s)).distanceTo(p);
    }

    private static @Nullable SegmentHit rayToSegment(Vec3 o, Vec3 u, Vec3 p, Vec3 q, double reach) {
        Vec3 v = q.subtract(p), w = o.subtract(p);
        double b = u.dot(v), c = v.dot(v), d = u.dot(w), e = v.dot(w);
        if (c < 1e-9) return null;

        double denom = c - b * b;
        double t = denom < 1e-9 ? 0 : Mth.clamp((e - b * d) / denom, 0, 1);
        double s = Mth.clamp(t * b - d, 0, reach);
        t = Mth.clamp((e + s * b) / c, 0, 1);
        s = Mth.clamp(t * b - d, 0, reach);

        Vec3 onRay = o.add(u.scale(s));
        Vec3 onSeg = p.add(v.scale(t));
        return new SegmentHit(onSeg, t, s, onRay.distanceTo(onSeg));
    }

    public record Hit(PowerlineGraph.Edge edge, Vec3 point, Vec3 tangent, double distance) {}

    private record SegmentHit(Vec3 point, double t, double along, double gap) {}
}
