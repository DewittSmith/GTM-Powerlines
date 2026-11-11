package com.github.dewittsmith.gtm_powerlines.graph;

import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import com.gregtechceu.gtceu.common.pipelike.cable.AveragingPerTickCounter;
import com.gregtechceu.gtceu.utils.GTMath;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.WireDeltaPacket;
import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class PowerlineNetwork extends SavedData {

    private static final String DATA_NAME = "gtm_powerlines_network";
    public static final int DEFAULT_TEMP = 293;

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

    private final Long2ObjectMap<List<TransferPath>> routes = new Long2ObjectOpenHashMap<>();
    private final Map<PowerlineGraph.Edge, AveragingPerTickCounter> amperageCounters = new HashMap<>();
    private final Map<PowerlineGraph.Edge, WireHeat> heat = new HashMap<>();
    private boolean heatTicking;

    private PowerlineNetwork(PowerlineGraph graph, ServerLevel level) {
        this.graph = graph;
        this.level = level;
    }

    public static PowerlineNetwork get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                tag -> load(level, tag),
                () -> new PowerlineNetwork(new PowerlineGraph(), level),
                DATA_NAME);
    }

    private List<TransferPath> getRoutes(BlockPos source) {
        var key = source.asLong();
        var result = routes.get(key);
        if (result == null) {
            result = TransferPath.walk(graph, source);
            routes.put(key, result);
        }
        return result;
    }

    public long acceptEnergy(BlockPos source, long voltage, long amperage) {
        long amperesUsed = 0L;
        for (var route : getRoutes(source)) {
            if (route.maxLoss() >= voltage) continue;

            var dest = route.getHandler(level);
            if (dest == null) continue;

            var facing = route.targetFacing().getOpposite();
            if (!dest.inputsEnergy(facing) || dest.getEnergyCanBeInserted() <= 0) continue;

            long pathVoltage = voltage - route.maxLoss();
            for (var edge : route.path()) {
                long maxVoltage = edge.stats().props().getVoltage();
                if (maxVoltage < voltage) {
                    applyHeat(edge, overvoltageHeat(voltage, maxVoltage));
                    pathVoltage = Math.min(maxVoltage, pathVoltage);
                }
            }

            long amps = dest.acceptEnergyFromNetwork(facing, pathVoltage, amperage - amperesUsed);
            if (amps == 0) continue;

            amperesUsed += amps;
            float voltageTraveled = voltage;
            for (var edge : route.path()) {
                voltageTraveled -= edge.loss();
                if (voltageTraveled <= 0) break;
                incrementAmperage(edge, amps);
            }

            if (amperage == amperesUsed) break;
        }
        return amperesUsed;
    }

    private static int overvoltageHeat(long voltage, long maxVoltage) {
        var tierDiff = Math.max(1, GTUtil.getTierByVoltage(voltage) - GTUtil.getTierByVoltage(maxVoltage));
        return (int) (Math.log(tierDiff) * 45 + 36.5);
    }

    private void incrementAmperage(PowerlineGraph.Edge edge, long amps) {
        var counter = amperageCounters.computeIfAbsent(edge, unused -> new AveragingPerTickCounter());
        counter.increment(level, amps);
        int dif = GTMath.saturatedCast(counter.getLast(level) - edge.stats().props().getAmperage());
        if (dif > 0) applyHeat(edge, dif * 40);
    }

    private void applyHeat(PowerlineGraph.Edge edge, int amount) {
        if (amount <= 0) return;
        heat.computeIfAbsent(edge, unused -> new WireHeat(DEFAULT_TEMP)).heatQueue += amount;
        startHeatTicking();
    }

    private void startHeatTicking() {
        if (heatTicking || heat.isEmpty()) return;
        heatTicking = true;
        TaskHandler.enqueueServerTask(level, this::tickHeat, 0);
    }

    private void tickHeat() {
        List<PowerlineGraph.Edge> melted = null;
        var iterator = heat.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var edge = entry.getKey();
            var wire = entry.getValue();

            if (wire.heatQueue > 0) wire.temperature += wire.heatQueue;

            if (wire.temperature >= CableBlockEntity.getMeltTemp()) {
                iterator.remove();
                if (melted == null) melted = new ArrayList<>();
                melted.add(edge);
                continue;
            }

            if (wire.temperature <= DEFAULT_TEMP) {
                iterator.remove();
                continue;
            }

            if (wire.heatQueue == 0) {
                wire.temperature = (int) (wire.temperature - Math.pow(wire.temperature - DEFAULT_TEMP, 0.35));
            } else {
                wire.heatQueue = 0;
            }

            if (wire.temperature >= 500 && level.getGameTime() % 5 == 0) spawnHeatParticles(edge, false);
        }

        if (melted != null) {
            for (var edge : melted) {
                spawnHeatParticles(edge, true);
                disconnect(edge.a(), edge.b());
            }
        }

        setDirty();
        if (heat.isEmpty()) heatTicking = false;
        else TaskHandler.enqueueServerTask(level, this::tickHeat, 0);
    }

    private void spawnHeatParticles(PowerlineGraph.Edge edge, boolean burned) {
        var start = WireGeometry.getAttachmentPoint(edge.a(), edge.dirA());
        var end = WireGeometry.getAttachmentPoint(edge.b(), edge.dirB());

        var sag = WireGeometry.sag(start, end);
        var samples = Mth.ceil(WireGeometry.samples(WireGeometry.length(start, end, sag)));

        for (var index = 0; index < samples; ++index) {
            var point = WireGeometry.sample(start, end, index / (samples - 1.0), sag);
            if (!level.isLoaded(BlockPos.containing(point))) continue;
            level.sendParticles(burned ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, point.x, point.y, point.z,
                    burned ? 3 : 1, 0.05, 0.05, 0.05, 0.02);
        }
    }

    public @Nullable PowerlineGraph.Edge connect(BlockPos start, Direction startDir, BlockPos end, Direction endDir,
                                                 ResourceLocation wire) {
        var edge = PowerlineGraph.Edge.of(start, startDir, end, endDir, wire);
        if (edge.stats() == null || !graph.add(edge)) return null;
        onChanged(new WireDeltaPacket(true, edge));
        return edge;
    }

    public boolean disconnect(BlockPos start, BlockPos end) {
        var edge = graph.disconnect(start, end);
        if (edge == null) return false;
        onRemoved(edge);
        return true;
    }

    public List<PowerlineGraph.Edge> disconnectAll(BlockPos start) {
        var removed = graph.disconnectAll(start);
        removed.forEach(this::onRemoved);
        return removed;
    }

    private void onRemoved(PowerlineGraph.Edge edge) {
        heat.remove(edge);
        amperageCounters.remove(edge);
        onChanged(new WireDeltaPacket(false, edge));
    }

    private void onChanged(WireDeltaPacket packet) {
        routes.clear();
        setDirty();
        Net.sendToDimension(level, packet);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        PowerlineGraph.serialize(graph, tag);

        var heatList = new ListTag();
        heat.forEach((edge, wire) -> {
            var entry = new CompoundTag();
            entry.putLong("a", edge.a().asLong());
            entry.putLong("b", edge.b().asLong());
            entry.putInt("temperature", wire.temperature + wire.heatQueue);
            heatList.add(entry);
        });
        tag.put("wireHeat", heatList);
        return tag;
    }

    private static PowerlineNetwork load(ServerLevel level, CompoundTag tag) {
        var network = new PowerlineNetwork(PowerlineGraph.deserialize(tag), level);

        for (var raw : tag.getList("wireHeat", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            var temperature = Math.min(entry.getInt("temperature"), CableBlockEntity.getMeltTemp());
            if (temperature <= DEFAULT_TEMP) continue;
            network.graph.getEdge(BlockPos.of(entry.getLong("a")), BlockPos.of(entry.getLong("b")))
                    .ifPresent(edge -> network.heat.put(edge, new WireHeat(temperature)));
        }

        network.startHeatTicking();
        return network;
    }
}
