package com.github.dewittsmith.gtm_powerlines.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Ray-casting against suspended cables (which are neither blocks nor entities).
 */
public final class WireTargeting {

    /**
     * @param edge     the cable that was hit
     * @param point    closest point on the cable
     * @param tangent  normalized cable direction at {@code point}
     * @param distance distance from the eye along the look ray
     */
    public record Hit(PowerlineGraph.Edge edge, Vec3 point, Vec3 tangent, double distance) {}

    /** How far (in blocks) from the cable centre the look ray may pass and still count as a hit. */
    private static final double TOLERANCE = 0.2;

    /**
     * Finds the cable the player is looking at, provided it is closer than any block in the way.
     */
    public static @Nullable Hit pick(PowerlineGraph graph, Player player, float partialTick) {
        double reach = player.getBlockReach();
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);

        var hit = raycast(graph, eye, look, reach);
        if (hit == null) return null;

        var block = player.pick(reach, partialTick, false);
        if (block.getType() != HitResult.Type.MISS && eye.distanceTo(block.getLocation()) <= hit.distance()) {
            return null;
        }
        return hit;
    }

    public static @Nullable Hit raycast(PowerlineGraph graph, Vec3 eye, Vec3 look, double reach) {
        // only cables indexed in chunks the look ray passes through
        Set<PowerlineGraph.Edge> candidates = new HashSet<>();
        var reachEnd = eye.add(look.scale(reach));
        for (long chunk : PowerlineGraph.getChunksCrossed(BlockPos.containing(eye), BlockPos.containing(reachEnd))) {
            candidates.addAll(graph.atChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)));
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

        Hit best = null;
        Vec3 prev = from;
        for (int i = 1; i < n; i++) {
            Vec3 next = WireGeometry.sample(from, to, i / (n - 1.0), sag);
            var result = rayToSegment(eye, look, prev, next, reach);
            if (result != null && result.gap <= tolerance && (best == null || result.along < best.distance)) {
                best = new Hit(edge, result.point, next.subtract(prev).normalize(), result.along);
            }
            prev = next;
        }
        return best;
    }

    private record SegmentHit(Vec3 point, double along, double gap) {}

    /** Closest approach between the ray {@code o + s*u} (0 ≤ s ≤ reach) and the segment pq. */
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
        return new SegmentHit(onSeg, s, onRay.distanceTo(onSeg));
    }
}
