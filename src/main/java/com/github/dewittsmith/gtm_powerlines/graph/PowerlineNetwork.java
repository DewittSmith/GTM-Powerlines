package com.github.dewittsmith.gtm_powerlines.graph;

import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.WireDeltaPacket;
import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import com.gregtechceu.gtceu.common.pipelike.cable.AveragingPerTickCounter;
import com.gregtechceu.gtceu.utils.GTMath;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.TaskHandler;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PowerlineNetwork extends SavedData {

    private record WireKey(long a, long b) {

        private static WireKey of(TransferPath.Edge edge) {
            return of(edge.a(), edge.b());
        }

        private static WireKey of(BlockPos a, BlockPos b) {
            var packedA = a.asLong();
            var packedB = b.asLong();
            return packedA <= packedB ? new WireKey(packedA, packedB) : new WireKey(packedB, packedA);
        }
    }

    private static final class WireHeat {

        private int temperature;
        private int heatQueue;

        private WireHeat(int temperature) {
            this.temperature = temperature;
        }
    }

    @Getter
    private final PowerlineGraph graph;

    private final ServerLevel level;

    private final Long2ObjectMap<List<TransferPath>> paths = new Long2ObjectOpenHashMap<>();
    private final Map<WireKey, AveragingPerTickCounter> usedAmperage = new HashMap<>();
    private final Map<WireKey, WireHeat> wireHeat = new HashMap<>();
    private boolean transferringEnergy;
    private TickableSubscription heatSubscription;

    public PowerlineNetwork(PowerlineGraph graph, ServerLevel level) {
        this.graph = graph;
        this.level = level;
    }

    private static ArrayList<TransferPath> arrayFactory(long key) {
        return new ArrayList<>();
    }

    @NonNull
    public static PowerlineNetwork get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(
                        tag -> PowerlineNetwork.load(level, tag),
                        () -> new PowerlineNetwork(new PowerlineGraph(), level),
                        "gtm_powerlines_network");
    }

    public boolean rebuildPath(BlockPos start, BlockPos end) {
        var key = start.asLong();
        var list = paths.computeIfAbsent(key, PowerlineNetwork::arrayFactory);
        for (var path : list) {
            if (path.getEnd().equals(end)) {
                return path.rebuild(graph);
            }
        }

        var path = new TransferPath(start, end);
        list.add(path);

        return path.rebuild(graph);
    }

    public void rebuildPaths() {
        for (var a : graph.allNodes()) {
            for (var b : graph.allNodes()) {
                if (a.equals(b)) continue;
                rebuildPath(a, b);
            }
        }
    }

    public long sendEnergy(BlockPos start, long voltage, long amperage) {
        if (transferringEnergy || voltage <= 0 || amperage <= 0) return 0L;

        var list = paths.getOrDefault(start.asLong(), Collections.emptyList());
        if (list.isEmpty()) return 0L;

        transferringEnergy = true;
        var deliveredAmperage = 0L;
        try {
            for (var path : list) {
                if (!path.hasPath()) continue;
                var targetEdge = path.lastEdge();
                var packetVoltage = voltage - (long) Math.ceil(path.getTotalLoss());
                if (packetVoltage <= 0) continue;

                for (var edge : path.getEdges()) {
                    if (voltage > edge.voltage()) {
                        packetVoltage = Math.min(packetVoltage, edge.voltage());
                        applyWireHeat(edge, voltageHeat(voltage, edge.voltage()));
                    }
                }
                if (packetVoltage <= 0) continue;

                var accepted = sendEnergyTo(path.getEnd(), targetEdge.dirB(), packetVoltage,
                        amperage - deliveredAmperage);
                if (accepted <= 0) continue;

                deliveredAmperage += accepted;
                var voltageLoss = 0f;
                for (var edge : path.getEdges()) {
                    voltageLoss += edge.loss();
                    var voltageTraveled = voltage - (long) Math.ceil(voltageLoss);
                    if (voltageTraveled <= 0) break;

                    var counter = usedAmperage.computeIfAbsent(WireKey.of(edge),
                            unused -> new AveragingPerTickCounter());
                    counter.increment(level, accepted);
                    var excessAmperage = GTMath.saturatedCast(counter.getLast(level) - edge.amperage());
                    if (excessAmperage > 0) {
                        applyWireHeat(edge, excessAmperage * 40);
                    }
                }

                if (deliveredAmperage >= amperage) break;
            }
        } finally {
            transferringEnergy = false;
        }

        return deliveredAmperage;
    }

    private void applyWireHeat(TransferPath.Edge edge, int amount) {
        if (amount <= 0) return;
        var key = WireKey.of(edge);
        var heat = wireHeat.computeIfAbsent(key, unused -> new WireHeat(getDefaultTemp()));
        heat.heatQueue += amount;
        subscribeHeat();
        setDirty();
    }

    private void subscribeHeat() {
        if (heatSubscription != null || wireHeat.isEmpty()) return;
        heatSubscription = new TickableSubscription(this::updateHeat);
        scheduleHeat(heatSubscription);
    }

    private void scheduleHeat(TickableSubscription subscription) {
        TaskHandler.enqueueServerTask(level, () -> {
            subscription.run();
            if (subscription.isStillSubscribed()) scheduleHeat(subscription);
        }, 0);
    }

    private void updateHeat() {
        var pathsChanged = false;
        var iterator = wireHeat.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var key = entry.getKey();
            var edge = graph.getEdge(BlockPos.of(key.a()), BlockPos.of(key.b())).orElse(null);
            if (edge == null) {
                iterator.remove();
                continue;
            }

            var heat = entry.getValue();
            if (heat.heatQueue > 0) {
                heat.temperature += heat.heatQueue;
            }

            if (heat.temperature >= CableBlockEntity.getMeltTemp()) {
                spawnHeatParticles(edge, true);
                graph.disconnect(edge.a(), edge.b());
                Net.sendToDimension(level, new WireDeltaPacket(false, edge));
                usedAmperage.remove(key);
                iterator.remove();
                pathsChanged = true;
            } else if (heat.temperature <= getDefaultTemp()) {
                iterator.remove();
            } else {
                if (edge.stats().insulation().isCable && heat.temperature >= 1500 && GTValues.RNG.nextFloat() < 0.1) {
                    edge.uninsulateCable();
                    heat.temperature = getDefaultTemp();
                    heat.heatQueue = 0;
                    // TODO
                    continue;
                }

                if (heat.temperature >= 500 && level.getGameTime() % 5 == 0) {
                    spawnHeatParticles(edge, false);
                }

                if (heat.heatQueue == 0) {
                    heat.temperature = (int) (heat.temperature -
                            Math.pow(heat.temperature - getDefaultTemp(), 0.35));
                } else {
                    heat.heatQueue = 0;
                }
            }
        }

        if (pathsChanged) rebuildPaths();
        setDirty();
        if (wireHeat.isEmpty()) {
            heatSubscription.unsubscribe();
            heatSubscription = null;
        }
    }

    private void spawnHeatParticles(PowerlineGraph.Edge edge, boolean burned) {
        var start = WireGeometry.getAttachmentPoint(edge.a(), edge.dirA());
        var end = WireGeometry.getAttachmentPoint(edge.b(), edge.dirB());

        var sag = WireGeometry.sag(start, end);
        var length = WireGeometry.length(start, end, sag);
        var samples = WireGeometry.samples(length);

        for (var index = 0; index < samples; ++index) {
            var point = WireGeometry.sample(start, end, index / (samples - 1.0), sag);
            if (!level.isLoaded(BlockPos.containing(point))) continue;
            level.sendParticles(burned ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, point.x, point.y, point.z,
                    burned ? 3 : 1, 0.05, 0.05, 0.05, 0.02);
        }
    }

    private static int voltageHeat(long voltage, long rating) {
        return (int) (Math.log(Math.max(1,
                GTUtil.getTierByVoltage(voltage) - GTUtil.getTierByVoltage(rating))) * 45 + 36.5);
    }

    public boolean connect(BlockPos start, Direction startDir, BlockPos end, Direction endDir, ResourceLocation wire) {
        if (!graph.connect(start, startDir, end, endDir, wire)) return false;

        var e = graph.getEdge(start, end).orElse(null);
        Net.sendToDimension(level, new WireDeltaPacket(true, e));

        setDirty();
        rebuildPaths();
        return true;
    }

    public boolean disconnect(BlockPos start, BlockPos end) {
        var e = graph.getEdge(start, end).orElse(null);
        if (!graph.disconnect(start, end)) return false;
        var key = WireKey.of(start, end);
        wireHeat.remove(key);
        usedAmperage.remove(key);

        Net.sendToDimension(level, new WireDeltaPacket(false, e));

        setDirty();
        rebuildPaths();
        return true;
    }

    public List<PowerlineGraph.Edge> disconnectAll(BlockPos start) {
        var result = graph.disconnectAll(start);

        result.forEach(e -> {
            var key = WireKey.of(e.a(), e.b());
            wireHeat.remove(key);
            usedAmperage.remove(key);
        });
        result.forEach(e -> Net.sendToDimension(level, new WireDeltaPacket(false, e)));

        setDirty();
        rebuildPaths();
        return result;
    }

    private long sendEnergyTo(BlockPos target, Direction side, long voltage, long amperage) {
        target = target.relative(side.getOpposite());

        if (!level.isLoaded(target)) return 0L;
        var be = level.getBlockEntity(target);
        if (be == null) return 0L;
        var container = be.getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, side).resolve();
        return container.map(c -> c.acceptEnergyFromNetwork(side, voltage, amperage)).orElse(0L);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        PowerlineGraph.serialize(graph, tag);

        var heatList = new ListTag();
        wireHeat.forEach((key, heat) -> {
            var entry = new CompoundTag();
            entry.putLong("a", key.a());
            entry.putLong("b", key.b());
            entry.putInt("temperature", heat.temperature + heat.heatQueue);
            heatList.add(entry);
        });
        tag.put("wireHeat", heatList);
        return tag;
    }

    public static PowerlineNetwork load(ServerLevel level, CompoundTag tag) {
        var graph = PowerlineGraph.deserialize(tag);
        var result = new PowerlineNetwork(graph, level);

        for (var raw : tag.getList("wireHeat", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            var key = new WireKey(entry.getLong("a"), entry.getLong("b"));
            var edge = graph.getEdge(BlockPos.of(key.a()), BlockPos.of(key.b())).orElse(null);
            if (entry.getInt("temperature") > getDefaultTemp() &&
                    edge != null) {
                result.wireHeat.put(key,
                        new WireHeat(Math.min(CableBlockEntity.getMeltTemp(), entry.getInt("temperature"))));
            }
        }

        result.rebuildPaths();
        result.subscribeHeat();
        return result;
    }

    public static int getDefaultTemp() {
        return 293;
    }
}
