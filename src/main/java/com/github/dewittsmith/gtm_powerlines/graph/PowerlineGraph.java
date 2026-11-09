package com.github.dewittsmith.gtm_powerlines.graph;

import com.github.dewittsmith.gtm_powerlines.wire.WireStats;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

public class PowerlineGraph {

    public record Edge(BlockPos a, Direction dirA, BlockPos b, Direction dirB, ResourceLocation wire, WireStats stats) {

        public static Edge of(BlockPos a, Direction dirA, BlockPos b, Direction dirB, ResourceLocation wire) {
            var stats = WireStats.of(wire).orElse(null);
            return a.asLong() <= b.asLong() ? new Edge(a.immutable(), dirA, b.immutable(), dirB, wire, stats) :
                    new Edge(b.immutable(), dirB, a.immutable(), dirA, wire, stats);
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

        public float loss() {
            return length() * stats.props().getLossPerBlock();
        }

        public float length() {
            return length(a, dirA, b, dirB);
        }

        public static int wireCount(BlockPos a, Direction dirA, BlockPos b, Direction dirB) {
            return Mth.ceil(length(a, dirA, b, dirB));
        }

        public int wireCount() {
            return wireCount(a, dirA, b, dirB);
        }

        public BlockPos other(BlockPos p) {
            return p.equals(a) ? b : a;
        }
    }

    private final Set<BlockPos> nodes = new HashSet<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Long2ObjectMap<List<Edge>> byPos = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<List<Edge>> byChunk = new Long2ObjectOpenHashMap<>();

    private static ArrayList<Edge> arrayFactory(long key) {
        return new ArrayList<>();
    }

    private static LongSet getChunksCrossed(BlockPos a, BlockPos b) {
        var result = new LongOpenHashSet();

        int x = SectionPos.blockToSectionCoord(a.getX()), z = SectionPos.blockToSectionCoord(a.getZ());
        int endX = SectionPos.blockToSectionCoord(b.getX()), endZ = SectionPos.blockToSectionCoord(b.getZ());

        int dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        int stepX = Integer.compare(dx, 0), stepZ = Integer.compare(dz, 0);

        // Distance to the next chunk boundary, in block coordinates.
        int distX = stepX > 0 ? SectionPos.sectionToBlockCoord(x + 1) - a.getX() :
                a.getX() - SectionPos.sectionToBlockCoord(x);

        int distZ = stepZ > 0 ? SectionPos.sectionToBlockCoord(z + 1) - a.getZ() :
                a.getZ() - SectionPos.sectionToBlockCoord(z);

        result.add(ChunkPos.asLong(x, z));

        while (x != endX || z != endZ) {
            if (stepX == 0) {
                z += stepZ;
                distZ += SectionPos.SECTION_SIZE;
            } else if (stepZ == 0) {
                x += stepX;
                distX += SectionPos.SECTION_SIZE;
            } else {
                long crossX = (long) distX * Math.abs(dz), crossZ = (long) distZ * Math.abs(dx);

                if (crossX < crossZ) {
                    x += stepX;
                    distX += SectionPos.SECTION_SIZE;
                } else if (crossZ < crossX) {
                    z += stepZ;
                    distZ += SectionPos.SECTION_SIZE;
                } else {
                    x += stepX;
                    z += stepZ;
                    distX += SectionPos.SECTION_SIZE;
                    distZ += SectionPos.SECTION_SIZE;
                }
            }

            result.add(ChunkPos.asLong(x, z));
        }

        return result;
    }

    private void add(Edge edge) {
        edges.add(edge);
        byPos.computeIfAbsent(edge.a.asLong(), PowerlineGraph::arrayFactory).add(edge);
        byPos.computeIfAbsent(edge.b.asLong(), PowerlineGraph::arrayFactory).add(edge);

        for (var chunk : getChunksCrossed(edge.a, edge.b)) {
            byChunk.computeIfAbsent(chunk, PowerlineGraph::arrayFactory).add(edge);
        }

        nodes.add(edge.a);
        nodes.add(edge.b);
    }

    private void remove(Edge edge) {
        edges.remove(edge);
        byPos.computeIfAbsent(edge.a.asLong(), PowerlineGraph::arrayFactory).remove(edge);
        byPos.computeIfAbsent(edge.b.asLong(), PowerlineGraph::arrayFactory).remove(edge);

        for (var chunk : getChunksCrossed(edge.a, edge.b)) {
            byChunk.computeIfAbsent(chunk, PowerlineGraph::arrayFactory).remove(edge);
        }
    }

    public Set<BlockPos> allNodes() {
        return nodes;
    }

    public List<Edge> all() {
        return edges;
    }

    public Optional<Edge> getEdge(BlockPos start, BlockPos end) {
        return byPos.getOrDefault(start.asLong(), Collections.emptyList()).stream().filter(x -> x.a.equals(end) || x.b.equals(end)).findFirst();
    }

    public List<Edge> atBlock(int x, int y, int z) {
        return byPos.getOrDefault(BlockPos.asLong(x, y, z), Collections.emptyList());
    }

    public List<Edge> atBlock(BlockPos pos) {
        return atBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    public List<Edge> atChunk(int x, int z) {
        return byChunk.getOrDefault(ChunkPos.asLong(x, z), Collections.emptyList());
    }

    public List<Edge> atChunk(ChunkPos pos) {
        return atChunk(pos.x, pos.z);
    }

    public boolean connect(BlockPos a, Direction dirA, BlockPos b, Direction dirB, ResourceLocation wire) {
        if (a.equals(b) || atBlock(a).stream().anyMatch(e -> e.other(a).equals(b))) return false;
        add(Edge.of(a, dirA, b, dirB, wire));
        return true;
    }

    public boolean disconnect(BlockPos a, BlockPos b) {
        for (var edge : atBlock(a)) {
            if (edge.other(a).equals(b)) {
                remove(edge);
                return true;
            }
        }
        return false;
    }

    public List<Edge> disconnectAll(BlockPos pos) {
        var bucket = atBlock(pos);
        if (bucket.isEmpty()) return Collections.emptyList();

        var removed = new ArrayList<>(bucket);
        for (var edge : removed) {
            remove(edge);
        }

        return removed;
    }

    public static CompoundTag serialize(PowerlineGraph graph, CompoundTag tag) {
        if (graph == null) return tag;

        var list = new ListTag();
        for (var edge : graph.edges) {
            var e = new CompoundTag();
            e.putLong("a", edge.a.asLong());
            e.putString("dirA", edge.dirA.getName());
            e.putLong("b", edge.b.asLong());
            e.putString("dirB", edge.dirB.getName());
            e.putString("wire", edge.wire.toString());
            list.add(e);
        }

        tag.put("edges", list);
        return tag;
    }

    public static PowerlineGraph deserialize(CompoundTag tag) {
        var graph = new PowerlineGraph();
        for (var raw : tag.getList("edges", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            graph.add(Edge.of(
                    BlockPos.of(t.getLong("a")),
                    Direction.byName(t.getString("dirA")),
                    BlockPos.of(t.getLong("b")),
                    Direction.byName(t.getString("dirB")),
                    ResourceLocation.parse(t.getString("wire"))));
        }
        return graph;
    }
}
