package com.github.dewittsmith.gtm_powerlines.graph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import javax.annotation.Nullable;

public class TransferPath {

    public record Edge(BlockPos a, Direction dirA, BlockPos b, Direction dirB, float loss, long voltage, long amperage,
                       boolean isCable, PowerlineGraph.Edge graphEdge) {}

    private record PathEntry(BlockPos pos, PowerlineGraph.Edge edge, float totalLoss) {}

    @Getter
    private final BlockPos start;

    @Getter
    private final BlockPos end;

    @Getter
    private final List<Edge> edges = new ArrayList<>();

    @Getter
    private float totalLoss = 0f;

    public TransferPath(BlockPos start, BlockPos end) {
        this.start = start;
        this.end = end;
    }

    public @Nullable Edge lastEdge() {
        if (!hasPath()) return null;
        return edges.get(edges.size() - 1);
    }

    public boolean hasPath() {
        return !edges.isEmpty();
    }

    public boolean rebuild(PowerlineGraph graph) {
        edges.clear();
        totalLoss = 0f;

        var atStart = graph.atBlock(start);
        if (atStart.isEmpty()) return false;

        var atEnd = graph.atBlock(end);
        if (atEnd.isEmpty()) return false;

        var visited = new LongOpenHashSet();
        var distances = new Long2FloatOpenHashMap();
        var backtracks = new Long2LongOpenHashMap();
        var backtrackEdges = new Long2ObjectOpenHashMap<PowerlineGraph.Edge>();

        distances.put(start.asLong(), 0);

        var queue = new PriorityQueue<>(Comparator.comparingDouble(PathEntry::totalLoss));
        for (var edge : atStart) {
            queue.offer(new PathEntry(start, edge, edge.loss()));
        }

        while (!queue.isEmpty()) {
            var current = queue.poll();
            var currentL = current.pos.asLong();

            var target = current.edge.other(current.pos);
            var targetL = target.asLong();

            if (current.totalLoss < distances.getOrDefault(targetL, Float.MAX_VALUE)) {
                distances.put(targetL, current.totalLoss);
                backtracks.put(targetL, currentL);
                backtrackEdges.put(targetL, current.edge);
            }

            if (!visited.add(currentL)) continue;

            var currentProps = current.edge.stats().props();
            for (var edge : graph.atBlock(target)) {
                var edgeProps = edge.stats().props();
                if (edgeProps.getVoltage() <= currentProps.getVoltage()) {
                    queue.offer(new PathEntry(target, edge, current.totalLoss + edge.loss()));
                }
            }
        }

        var current = end.asLong();
        while (current != start.asLong()) {
            if (!backtracks.containsKey(current)) {
                edges.clear();
                return false;
            }

            var edge = backtrackEdges.get(current);
            var swapAB = edge.a().asLong() == current;

            var props = edge.stats().props();
            var insulation = edge.stats().insulation();
            edges.add(0, new Edge(
                    swapAB ? edge.b() : edge.a(),
                    swapAB ? edge.dirB() : edge.dirA(),
                    swapAB ? edge.a() : edge.b(),
                    swapAB ? edge.dirA() : edge.dirB(),
                    edge.loss(),
                    props.getVoltage(),
                    props.getAmperage(),
                    insulation.isCable,
                    edge));
            totalLoss += edge.loss();
            current = backtracks.get(current);
        }

        return true;
    }
}
