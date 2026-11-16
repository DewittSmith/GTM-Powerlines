package com.github.dewittsmith.gtm_powerlines.graph;

import com.gregtechceu.gtceu.api.item.MaterialPipeBlockItem;
import com.gregtechceu.gtceu.common.block.CableBlock;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import com.gregtechceu.gtceu.common.data.GTMaterialBlocks;
import com.gregtechceu.gtceu.common.pipelike.cable.AveragingPerTickCounter;
import com.gregtechceu.gtceu.common.pipelike.cable.Insulation;
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
import net.minecraftforge.registries.ForgeRegistries;

import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.WireDeltaPacket;
import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import javax.annotation.Nullable;

public class PowerlineNetwork extends SavedData {

    public static final int DEFAULT_TEMP = 293;
    public static final int INSULATION_BURN_TEMP = 1500;
    private static final String DATA_NAME = "gtm_powerlines_network";
    private final PowerlineGraph graph;
    private final ServerLevel level;
    private final Long2ObjectMap<BlockPos> transformerNodes = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<RouteSet> routes = new Long2ObjectOpenHashMap<>();
    private final Map<PowerlineGraph.Edge, AveragingPerTickCounter> amperageCounters = new HashMap<>();
    private final Map<PowerlineGraph.Edge, AveragingPerTickCounter> energyCounters = new HashMap<>();
    private final Map<PowerlineGraph.Edge, long[]> voltages = new HashMap<>();
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

    private static int overvoltageHeat(long voltage, long maxVoltage) {
        var tierDiff = Math.max(1, GTUtil.getTierByVoltage(voltage) - GTUtil.getTierByVoltage(maxVoltage));
        return (int) (Math.log(tierDiff) * 45 + 36.5);
    }

    private static <V> void rekey(Map<PowerlineGraph.Edge, V> map, PowerlineGraph.Edge edge) {
        var value = map.remove(edge);
        if (value != null) map.put(edge, value);
    }

    private static @Nullable ResourceLocation bareWire(ResourceLocation wire, Insulation insulation) {
        if (insulation.insulationLevel < 0) return null;
        if (!(ForgeRegistries.ITEMS.getValue(wire) instanceof MaterialPipeBlockItem item) ||
                !(item.getBlock() instanceof CableBlock cable)) {
            return null;
        }
        var entry = GTMaterialBlocks.CABLE_BLOCKS.get(Insulation.values()[insulation.insulationLevel].tagPrefix,
                cable.material);
        return entry == null ? null : ForgeRegistries.ITEMS.getKey(entry.get().asItem());
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

        for (var raw : tag.getList("transformerNodes", Tag.TAG_COMPOUND)) {
            var node = (CompoundTag) raw;
            network.transformerNodes.put(node.getLong("insulator"), BlockPos.of(node.getLong("controller")));
        }

        network.startHeatTicking();
        return network;
    }

    public PowerlineGraph getGraph() {
        return graph;
    }

    public void setTransformerNode(BlockPos insulator, @Nullable BlockPos controller) {
        var key = insulator.asLong();
        var previous = controller == null ? transformerNodes.remove(key) :
                transformerNodes.put(key, controller.immutable());
        if (Objects.equals(controller, previous)) return;
        routes.clear();
        setDirty();
    }

    public boolean isTransformerNode(BlockPos insulator) {
        return transformerNodes.containsKey(insulator.asLong());
    }

    public @Nullable BlockPos getTransformerController(BlockPos insulator) {
        return transformerNodes.get(insulator.asLong());
    }

    public RouteSet getRoutes(BlockPos source) {
        var key = source.asLong();
        var result = routes.get(key);
        if (result == null) {
            var paths = TransferPath.walk(graph, source, this::isTransformerNode);
            var dedicated = isTransformerNode(source) ||
                    paths.stream().anyMatch(path -> isTransformerNode(path.targetPos()));
            result = new RouteSet(paths, dedicated);
            routes.put(key, result);
        }
        return result;
    }

    public @Nullable TransferPath findRoute(BlockPos from, BlockPos to) {
        for (var path : getRoutes(from).paths()) {
            if (path.targetPos().equals(to)) return path;
        }
        return null;
    }

    public long acceptEnergy(BlockPos source, long voltage, long amperage) {
        var routeSet = getRoutes(source);
        if (routeSet.dedicated()) return 0;

        long amperesUsed = 0L;
        for (var route : routeSet.paths()) {
            if (route.maxLoss() >= voltage) continue;

            var dest = route.getHandler(level);
            if (dest == null) continue;

            var facing = route.targetFacing().getOpposite();
            if (!dest.inputsEnergy(facing) || dest.getEnergyCanBeInserted() <= 0) continue;

            long pathVoltage = applyVoltageLimit(route, voltage, voltage - route.maxLoss());
            long amps = dest.acceptEnergyFromNetwork(facing, pathVoltage, amperage - amperesUsed);
            if (amps == 0) continue;

            amperesUsed += amps;
            recordFlow(route, voltage, amps, 1f);

            if (amperage == amperesUsed) break;
        }
        return amperesUsed;
    }

    public long applyVoltageLimit(TransferPath route, long voltage, long pathVoltage) {
        for (var edge : route.path()) {
            long maxVoltage = edge.stats().props().getVoltage();
            if (maxVoltage < voltage) {
                applyHeat(edge, overvoltageHeat(voltage, maxVoltage));
                pathVoltage = Math.min(maxVoltage, pathVoltage);
            }
        }
        return pathVoltage;
    }

    public void recordFlow(TransferPath route, long voltage, long amps, float lossScale) {
        float voltageTraveled = voltage;
        for (var edge : route.path()) {
            voltageTraveled -= edge.loss() * lossScale;
            if (voltageTraveled <= 0) break;
            incrementAmperage(edge, amps);
            var seen = voltages.computeIfAbsent(edge, unused -> new long[2]);
            long now = level.getGameTime();
            if (seen[0] != now) {
                seen[0] = now;
                seen[1] = 0;
            }
            seen[1] = Math.max(seen[1], voltage);
            energyCounters.computeIfAbsent(edge, unused -> new AveragingPerTickCounter())
                    .increment(level, (long) voltageTraveled * amps);
        }
    }

    private void incrementAmperage(PowerlineGraph.Edge edge, long amps) {
        var counter = amperageCounters.computeIfAbsent(edge, unused -> new AveragingPerTickCounter());
        counter.increment(level, amps);
        int dif = GTMath.saturatedCast(counter.getLast(level) - edge.stats().props().getAmperage());
        if (dif > 0) applyHeat(edge, dif * 40);
    }

    public double getAverageAmperage(PowerlineGraph.Edge edge) {
        var counter = amperageCounters.get(edge);
        return counter == null ? 0 : counter.getAverage(level);
    }

    public double getAverageEnergy(PowerlineGraph.Edge edge) {
        var counter = energyCounters.get(edge);
        return counter == null ? 0 : counter.getAverage(level);
    }

    public long getCurrentVoltage(PowerlineGraph.Edge edge) {
        var seen = voltages.get(edge);
        return seen == null || level.getGameTime() - seen[0] > 20 ? 0 : seen[1];
    }

    public int getTemperature(PowerlineGraph.Edge edge) {
        var wire = heat.get(edge);
        return wire == null ? DEFAULT_TEMP : wire.temperature;
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
        List<PowerlineGraph.Edge> stripped = null;
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

            if (edge.stats().insulation().insulationLevel >= 0 && wire.temperature >= INSULATION_BURN_TEMP &&
                    level.random.nextFloat() < 0.1f) {
                if (stripped == null) stripped = new ArrayList<>();
                stripped.add(edge);
                wire.heatQueue = 0;
                continue;
            }

            if (wire.heatQueue == 0) {
                wire.temperature = (int) (wire.temperature - Math.pow(wire.temperature - DEFAULT_TEMP, 0.35));
            } else {
                wire.heatQueue = 0;
            }

            if (wire.temperature >= 500 && level.getGameTime() % 5 == 0) spawnHeatParticles(edge, false);
        }

        if (stripped != null) stripped.forEach(this::uninsulate);

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

    private void uninsulate(PowerlineGraph.Edge edge) {
        var bare = bareWire(edge.wire(), edge.stats().insulation());
        if (bare == null) return;
        var replacement = PowerlineGraph.Edge.of(edge.a(), edge.dirA(), edge.b(), edge.dirB(), bare);
        if (replacement.stats() == null || !graph.replace(replacement)) return;

        rekey(heat, replacement);
        rekey(amperageCounters, replacement);
        rekey(energyCounters, replacement);
        rekey(voltages, replacement);

        spawnHeatParticles(replacement, false);
        routes.clear();
        setDirty();
        Net.sendToDimension(level, new WireDeltaPacket(WireDeltaPacket.Op.UPDATE, replacement));
    }

    public void burnAll(BlockPos pos) {
        for (var edge : List.copyOf(graph.atBlock(pos))) {
            spawnHeatParticles(edge, true);
            disconnect(edge.a(), edge.b());
        }
    }

    private void spawnHeatParticles(PowerlineGraph.Edge edge, boolean burned) {
        var start = WireGeometry.getAttachmentPoint(edge.a(), edge.dirA(), edge.stats());
        var end = WireGeometry.getAttachmentPoint(edge.b(), edge.dirB(), edge.stats());

        var sag = WireGeometry.sag(start, end);
        var samples = Math.max(2, Mth.ceil(WireGeometry.samples(WireGeometry.length(start, end, sag))));

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
        onChanged(new WireDeltaPacket(WireDeltaPacket.Op.ADD, edge));
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

    public boolean repaint(PowerlineGraph.Edge edge, int paint) {
        var current = graph.getEdge(edge.a(), edge.b()).orElse(null);
        if (current == null || current.paint() == paint) return false;
        var painted = current.withPaint(paint);
        graph.replace(painted);
        setDirty();
        Net.sendToDimension(level, new WireDeltaPacket(WireDeltaPacket.Op.UPDATE, painted));
        return true;
    }

    private void onRemoved(PowerlineGraph.Edge edge) {
        heat.remove(edge);
        amperageCounters.remove(edge);
        energyCounters.remove(edge);
        voltages.remove(edge);
        onChanged(new WireDeltaPacket(WireDeltaPacket.Op.REMOVE, edge));
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

        var nodeList = new ListTag();
        for (var entry : transformerNodes.long2ObjectEntrySet()) {
            var node = new CompoundTag();
            node.putLong("insulator", entry.getLongKey());
            node.putLong("controller", entry.getValue().asLong());
            nodeList.add(node);
        }
        tag.put("transformerNodes", nodeList);
        return tag;
    }

    private static final class WireHeat {

        private int temperature;
        private int heatQueue;

        private WireHeat(int temperature) {
            this.temperature = temperature;
        }
    }

    public record RouteSet(List<TransferPath> paths, boolean dedicated) {}
}
