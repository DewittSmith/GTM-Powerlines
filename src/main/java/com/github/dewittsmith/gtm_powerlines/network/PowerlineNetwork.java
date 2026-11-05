package com.github.dewittsmith.gtm_powerlines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.machines.PowerlineTowerMachine;
import com.github.dewittsmith.gtm_powerlines.machines.PowerlineTransformerMachine;
import com.github.dewittsmith.gtm_powerlines.util.BlockUtils;
import com.github.dewittsmith.gtm_powerlines.wire.WireInfo;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.*;

public class PowerlineNetwork extends SavedData {

    public record Edge(BlockPos a, BlockPos b, ResourceLocation wire) {

        public static Edge of(BlockPos x, BlockPos y, ResourceLocation wire) {
            return x.asLong() <= y.asLong() ? new Edge(x.immutable(), y.immutable(), wire) :
                    new Edge(y.immutable(), x.immutable(), wire);
        }

        public static int wireCount(BlockPos a, BlockPos b) {
            return Mth.ceil(Math.sqrt(a.distSqr(b)));
        }

        public int wireCount() {
            return wireCount(a, b);
        }

        public BlockPos other(BlockPos p) {
            return p.equals(a) ? b : a;
        }
    }

    public record TransferEdge(BlockPos a, BlockPos b, float totalLoss) {}

    private final Set<Edge> edges = new LinkedHashSet<>();
    private final Long2ObjectMap<List<Edge>> byPos = new Long2ObjectOpenHashMap<>();

    private final Long2ObjectMap<List<TransferEdge>> transfers = new Long2ObjectOpenHashMap<>();

    private enum NodeKind {
        TOWER,
        INPUT,
        OUTPUT,
        UNKNOWN
    }

    private record Step(long pos, float loss) {}

    public static PowerlineNetwork get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> {
            var network = PowerlineNetwork.load(tag);
            network.setTransferDirty();
            return network;
        }, PowerlineNetwork::new, "gtm_powerlines_network");
    }

    public Collection<Edge> all() {
        return Collections.unmodifiableSet(edges);
    }

    public List<Edge> at(BlockPos pos) {
        return byPos.getOrDefault(pos.asLong(), List.of());
    }

    public List<TransferEdge> transferAt(BlockPos pos) {
        return transfers.getOrDefault(pos.asLong(), List.of());
    }

    public boolean connect(ServerLevel level, BlockPos x, BlockPos y, ResourceLocation wire) {
        Edge e = Edge.of(x, y, wire);
        if (x.equals(y) || at(x).stream().anyMatch(o -> o.other(x).equals(y))) return false;
        add(e);
        setDirty();
        setTransferDirty();
        Net.sendToDimension(level, new WireDeltaPacket(true, e));
        return true;
    }

    public boolean disconnect(ServerLevel level, BlockPos x, BlockPos y) {
        for (Edge e : at(x)) {
            if (e.other(x).equals(y)) {
                remove(e);
                setDirty();
                setTransferDirty();
                Net.sendToDimension(level, new WireDeltaPacket(false, e));
                return true;
            }
        }
        return false;
    }

    public List<Edge> disconnectAll(ServerLevel level, BlockPos pos) {
        List<Edge> removed = new ArrayList<>(at(pos));
        for (Edge e : removed) {
            remove(e);
            Net.sendToDimension(level, new WireDeltaPacket(false, e));
        }
        if (!removed.isEmpty()) {
            setDirty();
            setTransferDirty();
        }

        return removed;
    }

    private boolean isTransferDirty;

    public void setTransferDirty() {
        isTransferDirty = true;
    }

    public boolean rebuildTransferEdgesIfDirty(ServerLevel level) {
        if (!isTransferDirty) return false;
        isTransferDirty = false;
        rebuildTransferEdges(level);
        return true;
    }

    public void rebuildTransferEdges(ServerLevel level) {
        transfers.clear();
        for (var key : byPos.keySet()) {
            var pos = BlockPos.of(key);
            var insulatorOpt = BlockUtils.instanceOf(level, pos, PowerlineInsulatorBlockEntity.class, true);
            if (insulatorOpt.isEmpty()) continue;
            var transformerOpt = BlockUtils.instanceOf(level, insulatorOpt.get().getController(),
                    PowerlineTransformerMachine.class, true);
            if (transformerOpt.isEmpty()) continue;
            rebuildTransferEdges(level, pos, transformerOpt.get());
        }
    }

    private void rebuildTransferEdges(ServerLevel level, BlockPos insulator, PowerlineTransformerMachine transformer) {
        if (transformer.isOutput()) return;

        List<TransferEdge> found = new ArrayList<>();
        Long2FloatMap best = new Long2FloatOpenHashMap();
        best.defaultReturnValue(Float.MAX_VALUE);
        PriorityQueue<Step> queue = new PriorityQueue<>(Comparator.comparingDouble(Step::loss));

        best.put(insulator.asLong(), 0f);
        queue.add(new Step(insulator.asLong(), 0f));

        while (!queue.isEmpty()) {
            Step step = queue.poll();
            if (step.loss() > best.get(step.pos())) continue;
            BlockPos current = BlockPos.of(step.pos());

            if (!current.equals(insulator)) {
                switch (classify(level, current)) {
                    case OUTPUT -> {
                        found.add(new TransferEdge(insulator, current, step.loss()));
                        continue;
                    }
                    case INPUT, UNKNOWN -> {
                        continue;
                    }
                    case TOWER -> { /* keep expanding */ }
                }
            }

            for (Edge edge : at(current)) {
                BlockPos next = edge.other(current);
                float loss = step.loss() + edgeLoss(edge);
                if (loss < best.get(next.asLong())) {
                    best.put(next.asLong(), loss);
                    queue.add(new Step(next.asLong(), loss));
                }
            }
        }

        if (!found.isEmpty()) transfers.put(insulator.asLong(), found);
    }

    private static NodeKind classify(ServerLevel level, BlockPos insulator) {
        if (!(level.getBlockEntity(insulator) instanceof PowerlineInsulatorBlockEntity be) ||
                be.getController() == null)
            return NodeKind.UNKNOWN;

        BlockPos controller = be.getController();
        var transformer = BlockUtils.instanceOf(level, controller, PowerlineTransformerMachine.class, true);
        if (transformer.isPresent()) return transformer.get().isOutput() ? NodeKind.OUTPUT : NodeKind.INPUT;
        return BlockUtils.instanceOf(level, controller, PowerlineTowerMachine.class, true).isPresent() ?
                NodeKind.TOWER :
                NodeKind.UNKNOWN;
    }

    private float edgeLoss(Edge edge) {
        var stats = WireInfo.of(edge.wire());
        if (stats.isEmpty()) return Float.MAX_VALUE;
        return (float) Math.sqrt(edge.a.distSqr(edge.b)) * stats.get().lossPerBlock();
    }

    private void add(Edge e) {
        edges.add(e);
        index(e.a, e);
        index(e.b, e);
    }

    private void remove(Edge e) {
        edges.remove(e);
        unindex(e.a, e);
        unindex(e.b, e);
    }

    private void index(BlockPos p, Edge e) {
        byPos.computeIfAbsent(p.asLong(), k -> new ArrayList<>()).add(e);
    }

    private void unindex(BlockPos p, Edge e) {
        var l = byPos.get(p.asLong());
        if (l != null) {
            l.remove(e);
            if (l.isEmpty()) byPos.remove(p.asLong());
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Edge e : edges) {
            CompoundTag t = new CompoundTag();
            t.putLong("a", e.a.asLong());
            t.putLong("b", e.b.asLong());
            t.putString("wire", e.wire().toString());
            list.add(t);
        }
        tag.put("edges", list);
        return tag;
    }

    public static PowerlineNetwork load(CompoundTag tag) {
        var n = new PowerlineNetwork();
        for (Tag raw : tag.getList("edges", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            n.add(Edge.of(BlockPos.of(t.getLong("a")), BlockPos.of(t.getLong("b")),
                    new ResourceLocation(t.getString("wire"))));
        }

        return n;
    }
}
