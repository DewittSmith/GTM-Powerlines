package com.github.dewittsmith.gtm_powerlines.network;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class PowerlineNetwork extends SavedData {
    public record Edge(BlockPos a, BlockPos b, ResourceLocation wire) {
        public static Edge of(BlockPos x, BlockPos y, ResourceLocation wire) {
            return x.asLong() <= y.asLong() ? new Edge(x.immutable(), y.immutable(), wire)
                    : new Edge(y.immutable(), x.immutable(), wire);
        }

        public BlockPos other(BlockPos p) {
            return p.equals(a) ? b : a;
        }
    }

    private final Set<Edge> edges = new LinkedHashSet<>();
    private final Long2ObjectMap<List<Edge>> byPos = new Long2ObjectOpenHashMap<>();

    public static PowerlineNetwork get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PowerlineNetwork::load, PowerlineNetwork::new, "gtm_powerlines_network");
    }

    public Collection<Edge> all() {
        return Collections.unmodifiableSet(edges);
    }

    public List<Edge> at(BlockPos pos) {
        return byPos.getOrDefault(pos.asLong(), List.of());
    }

    public boolean connect(ServerLevel level, BlockPos x, BlockPos y, ResourceLocation wire) {
        Edge e = Edge.of(x, y, wire);
        if (x.equals(y) || at(x).stream().anyMatch(o -> o.other(x).equals(y))) return false;
        add(e);
        setDirty();
        Net.sendToDimension(level, new WireDeltaPacket(true, e));
        return true;
    }

    public boolean disconnect(ServerLevel level, BlockPos x, BlockPos y) {
        for (Edge e : at(x)) {
            if (e.other(x).equals(y)) {
                remove(e);
                setDirty();
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
        if (!removed.isEmpty()) setDirty();
        return removed;
    }

    private void add(Edge e) {
        edges.add(e);
        index(e.a(), e);
        index(e.b(), e);
    }

    private void remove(Edge e) {
        edges.remove(e);
        unindex(e.a(), e);
        unindex(e.b(), e);
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
            t.putLong("a", e.a().asLong());
            t.putLong("b", e.b().asLong());
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
            n.add(Edge.of(BlockPos.of(t.getLong("a")), BlockPos.of(t.getLong("b")), new ResourceLocation(t.getString("wire"))));
        }
        return n;
    }
}
