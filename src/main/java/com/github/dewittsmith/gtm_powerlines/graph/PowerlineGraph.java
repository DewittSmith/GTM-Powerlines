package com.github.dewittsmith.gtm_powerlines.graph;

import com.gregtechceu.gtceu.api.blockentity.IPaintable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.wire.WireStats;
import it.unimi.dsi.fastutil.longs.*;

import java.util.*;

import javax.annotation.Nullable;

public class PowerlineGraph {

    private final Set<Edge> edges = new LinkedHashSet<>();
    private final Long2ObjectMap<List<Edge>> byPos = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<List<Edge>> byChunk = new Long2ObjectOpenHashMap<>();

    private static ArrayList<Edge> arrayFactory(long key) {
        return new ArrayList<>();
    }

    public static LongSet getChunksCrossed(BlockPos a, BlockPos b) {
        var result = new LongOpenHashSet();

        int x = SectionPos.blockToSectionCoord(a.getX()), z = SectionPos.blockToSectionCoord(a.getZ());
        int endX = SectionPos.blockToSectionCoord(b.getX()), endZ = SectionPos.blockToSectionCoord(b.getZ());
        result.add(ChunkPos.asLong(x, z));

        double ax = a.getX() + 0.5, az = a.getZ() + 0.5;
        double dx = b.getX() + 0.5 - ax, dz = b.getZ() + 0.5 - az;
        int stepX = Integer.signum(endX - x), stepZ = Integer.signum(endZ - z);

        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY :
                (SectionPos.sectionToBlockCoord(stepX > 0 ? x + 1 : x) - ax) / dx;
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY :
                (SectionPos.sectionToBlockCoord(stepZ > 0 ? z + 1 : z) - az) / dz;
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : SectionPos.SECTION_SIZE / Math.abs(dx);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : SectionPos.SECTION_SIZE / Math.abs(dz);

        int steps = Math.abs(endX - x) + Math.abs(endZ - z);
        for (int i = 0; i < steps; i++) {
            boolean moveX = x != endX && (z == endZ || tMaxX <= tMaxZ);
            if (x != endX && z != endZ && Math.abs(tMaxX - tMaxZ) < 1e-9) {
                result.add(ChunkPos.asLong(x + stepX, z));
                result.add(ChunkPos.asLong(x, z + stepZ));
            }
            if (moveX) {
                x += stepX;
                tMaxX += tDeltaX;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            result.add(ChunkPos.asLong(x, z));
        }

        return result;
    }

    private static void removeFromBucket(Long2ObjectMap<List<Edge>> map, long key, Edge edge) {
        var bucket = map.get(key);
        if (bucket == null) return;
        bucket.remove(edge);
        if (bucket.isEmpty()) map.remove(key);
    }

    private static void replaceInBucket(Long2ObjectMap<List<Edge>> map, long key, Edge edge) {
        var bucket = map.get(key);
        if (bucket == null) return;
        var index = bucket.indexOf(edge);
        if (index >= 0) bucket.set(index, edge);
    }

    public static CompoundTag serialize(PowerlineGraph graph, CompoundTag tag) {
        var list = new ListTag();
        for (var edge : graph.edges) {
            var e = new CompoundTag();
            e.putLong("a", edge.a.asLong());
            e.putString("dirA", edge.dirA.getName());
            e.putLong("b", edge.b.asLong());
            e.putString("dirB", edge.dirB.getName());
            e.putString("wire", edge.wire.toString());
            if (edge.isPainted()) e.putInt("paint", edge.paint);
            list.add(e);
        }

        tag.put("edges", list);
        return tag;
    }

    public static PowerlineGraph deserialize(CompoundTag tag) {
        var graph = new PowerlineGraph();
        for (var raw : tag.getList("edges", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            var edge = Edge.of(
                    BlockPos.of(t.getLong("a")),
                    Direction.byName(t.getString("dirA")),
                    BlockPos.of(t.getLong("b")),
                    Direction.byName(t.getString("dirB")),
                    ResourceLocation.parse(t.getString("wire")),
                    t.contains("paint", Tag.TAG_INT) ? t.getInt("paint") : IPaintable.UNPAINTED_COLOR);
            if (edge.stats() == null) {
                GTMPowerlines.LOGGER.warn("Dropping powerline with unknown wire {}", edge.wire());
                continue;
            }
            graph.add(edge);
        }
        return graph;
    }

    public boolean add(Edge edge) {
        if (edge.a.equals(edge.b) || getEdge(edge.a, edge.b).isPresent()) return false;
        edges.add(edge);
        byPos.computeIfAbsent(edge.a.asLong(), PowerlineGraph::arrayFactory).add(edge);
        byPos.computeIfAbsent(edge.b.asLong(), PowerlineGraph::arrayFactory).add(edge);
        for (var chunk : getChunksCrossed(edge.a, edge.b)) {
            byChunk.computeIfAbsent(chunk, PowerlineGraph::arrayFactory).add(edge);
        }
        return true;
    }

    private void remove(Edge edge) {
        edges.remove(edge);
        removeFromBucket(byPos, edge.a.asLong(), edge);
        removeFromBucket(byPos, edge.b.asLong(), edge);
        for (var chunk : getChunksCrossed(edge.a, edge.b)) {
            removeFromBucket(byChunk, chunk, edge);
        }
    }

    public Collection<Edge> all() {
        return Collections.unmodifiableCollection(edges);
    }

    public Long2ObjectMap<List<Edge>> byChunk() {
        return Long2ObjectMaps.unmodifiable(byChunk);
    }

    public List<Edge> atChunk(int x, int z) {
        return byChunk.getOrDefault(ChunkPos.asLong(x, z), Collections.emptyList());
    }

    public List<Edge> atChunk(ChunkPos pos) {
        return atChunk(pos.x, pos.z);
    }

    public Optional<Edge> getEdge(BlockPos start, BlockPos end) {
        for (var edge : atBlock(start)) {
            if (edge.other(start).equals(end)) return Optional.of(edge);
        }
        return Optional.empty();
    }

    public LongSet nodes() {
        return LongSets.unmodifiable(byPos.keySet());
    }

    public List<Edge> atBlock(BlockPos pos) {
        return byPos.getOrDefault(pos.asLong(), Collections.emptyList());
    }

    public boolean connect(BlockPos a, Direction dirA, BlockPos b, Direction dirB, ResourceLocation wire) {
        return add(Edge.of(a, dirA, b, dirB, wire));
    }

    public boolean replace(Edge edge) {
        var old = getEdge(edge.a, edge.b).orElse(null);
        if (old == null) return false;
        edges.remove(old);
        edges.add(edge);
        replaceInBucket(byPos, edge.a.asLong(), edge);
        replaceInBucket(byPos, edge.b.asLong(), edge);
        for (var chunk : getChunksCrossed(edge.a, edge.b)) {
            replaceInBucket(byChunk, chunk, edge);
        }
        return true;
    }

    public @Nullable Edge disconnect(BlockPos a, BlockPos b) {
        var edge = getEdge(a, b).orElse(null);
        if (edge != null) remove(edge);
        return edge;
    }

    public List<Edge> disconnectAll(BlockPos pos) {
        var removed = List.copyOf(atBlock(pos));
        removed.forEach(this::remove);
        return removed;
    }

    public void clear() {
        edges.clear();
        byPos.clear();
        byChunk.clear();
    }

    public record Edge(BlockPos a, Direction dirA, BlockPos b, Direction dirB, ResourceLocation wire, WireStats stats,
                       int paint) {

        public static Edge of(BlockPos a, Direction dirA, BlockPos b, Direction dirB, ResourceLocation wire) {
            return of(a, dirA, b, dirB, wire, IPaintable.UNPAINTED_COLOR);
        }

        public static Edge of(BlockPos a, Direction dirA, BlockPos b, Direction dirB, ResourceLocation wire,
                              int paint) {
            var stats = WireStats.of(wire).orElse(null);
            return a.asLong() <= b.asLong() ? new Edge(a.immutable(), dirA, b.immutable(), dirB, wire, stats, paint) :
                    new Edge(b.immutable(), dirB, a.immutable(), dirA, wire, stats, paint);
        }

        public static float length(BlockPos a, Direction dirA, BlockPos b, Direction dirB) {
            var ax = a.getX() + 0.5f - dirA.getStepX() * 0.5f;
            var ay = a.getY() + 0.5f - dirA.getStepY() * 0.5f;
            var az = a.getZ() + 0.5f - dirA.getStepZ() * 0.5f;

            var bx = b.getX() + 0.5f - dirB.getStepX() * 0.5f;
            var by = b.getY() + 0.5f - dirB.getStepY() * 0.5f;
            var bz = b.getZ() + 0.5f - dirB.getStepZ() * 0.5f;

            float dx = bx - ax, dy = by - ay, dz = bz - az;
            return Mth.sqrt(dx * dx + dy * dy + dz * dz);
        }

        public static int wireCount(BlockPos a, Direction dirA, BlockPos b, Direction dirB) {
            return Mth.ceil(length(a, dirA, b, dirB));
        }

        public Edge withPaint(int paint) {
            return new Edge(a, dirA, b, dirB, wire, stats, paint);
        }

        public boolean isPainted() {
            return paint != IPaintable.UNPAINTED_COLOR;
        }

        public int renderColor() {
            return isPainted() ? 0xFF000000 | paint : stats.color();
        }

        public float loss() {
            return length() * stats.props().getLossPerBlock();
        }

        public float length() {
            return length(a, dirA, b, dirB);
        }

        public int wireCount() {
            return wireCount(a, dirA, b, dirB);
        }

        public BlockPos other(BlockPos p) {
            return p.equals(a) ? b : a;
        }

        public Direction dirAt(BlockPos p) {
            return p.equals(a) ? dirA : dirB;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof Edge e && a.equals(e.a) && b.equals(e.b));
        }

        @Override
        public int hashCode() {
            return 31 * a.hashCode() + b.hashCode();
        }
    }
}
