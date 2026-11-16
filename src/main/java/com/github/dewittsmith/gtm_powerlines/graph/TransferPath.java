package com.github.dewittsmith.gtm_powerlines.graph;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Predicate;

import javax.annotation.Nullable;

public record TransferPath(BlockPos targetPos, Direction targetFacing, PowerlineGraph.Edge[] path, int distance,
                           float loss, long maxLoss, long minVoltage, long minAmperage) {

    public static List<TransferPath> walk(PowerlineGraph graph, BlockPos source) {
        return walk(graph, source, pos -> false);
    }

    public static List<TransferPath> walk(PowerlineGraph graph, BlockPos source, Predicate<BlockPos> terminal) {
        var distances = new Long2IntOpenHashMap();
        distances.defaultReturnValue(Integer.MAX_VALUE);
        var parents = new Long2ObjectOpenHashMap<PowerlineGraph.Edge>();
        var reached = new ArrayList<BlockPos>();

        var queue = new PriorityQueue<>(Comparator.comparingInt(Visit::distance));
        distances.put(source.asLong(), 0);
        queue.add(new Visit(source, 0));

        while (!queue.isEmpty()) {
            var visit = queue.poll();
            if (visit.distance > distances.get(visit.pos.asLong())) continue;
            var isSource = visit.pos.equals(source);
            if (!isSource) {
                reached.add(visit.pos);
                if (terminal.test(visit.pos)) continue;
            }

            for (var edge : graph.atBlock(visit.pos)) {
                var next = edge.other(visit.pos);
                var distance = visit.distance + edge.wireCount();
                if (distance < distances.get(next.asLong())) {
                    distances.put(next.asLong(), distance);
                    parents.put(next.asLong(), edge);
                    queue.add(new Visit(next, distance));
                }
            }
        }

        var routes = new ArrayList<TransferPath>(reached.size());
        for (var target : reached) {
            var path = new ArrayList<PowerlineGraph.Edge>();
            var loss = 0f;
            var minVoltage = Long.MAX_VALUE;
            var minAmperage = Long.MAX_VALUE;
            for (var pos = target; !pos.equals(source);) {
                var edge = parents.get(pos.asLong());
                path.add(edge);
                loss += edge.loss();
                minVoltage = Math.min(minVoltage, edge.stats().props().getVoltage());
                minAmperage = Math.min(minAmperage, edge.stats().props().getAmperage());
                pos = edge.other(pos);
            }

            var edges = new PowerlineGraph.Edge[path.size()];
            for (int i = 0; i < edges.length; i++) edges[i] = path.get(edges.length - 1 - i);

            var facing = edges[edges.length - 1].dirAt(target).getOpposite();
            routes.add(new TransferPath(target, facing, edges, distances.get(target.asLong()), loss, Mth.ceil(loss),
                    minVoltage, minAmperage));
        }
        return routes;
    }

    public @Nullable IEnergyContainer getHandler(Level level) {
        var pos = targetPos.relative(targetFacing);
        if (!level.isLoaded(pos)) return null;
        return GTCapabilityHelper.getEnergyContainer(level, pos, targetFacing.getOpposite());
    }

    public float length() {
        var total = 0f;
        for (var edge : path) total += edge.length();
        return total;
    }

    private record Visit(BlockPos pos, int distance) {}
}
