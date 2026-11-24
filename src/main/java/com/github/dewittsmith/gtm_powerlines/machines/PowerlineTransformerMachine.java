package com.github.dewittsmith.gtm_powerlines.machines;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.feature.IRecipeLogicModifierTrait;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.multiblock.error.CoilMatchingError;
import com.gregtechceu.gtceu.api.multiblock.error.PatternStringError;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.common.mui.GTMultiblockTextUtil;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.DoubleSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.LongSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.util.BlockUtils;
import com.github.dewittsmith.gtm_powerlines.util.CoilEfficiency;
import com.github.dewittsmith.gtm_powerlines.util.PowerlinePredicates;

import java.util.*;

import javax.annotation.Nullable;

import static com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries.INSULATOR;

public class PowerlineTransformerMachine extends WorkableMultiblockMachine
                                         implements IMuiMachine, ITieredMachine {

    private ICoilType coilType = CoilBlock.CoilType.CUPRONICKEL;

    private long networkOutputPerSec;

    private long energyOutputPerSec;

    private long networkOutput;
    private long energyOutput;

    private long networkInputPerSec;
    private long networkInput;
    private long receivedTick = -1;
    private long receiveCredit;
    private long sendCredit;

    private List<IEnergyContainer> hatches = Collections.emptyList();
    private @Nullable EnergyContainerList energyContainer;
    private Collection<BlockPos> insulators = Collections.emptyList();

    private @Nullable TickableSubscription transferSub;

    public PowerlineTransformerMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    public ICoilType getCoilType() {
        return coilType;
    }

    public long getNetworkOutputPerSec() {
        return networkOutputPerSec;
    }

    public long getEnergyOutputPerSec() {
        return energyOutputPerSec;
    }

    @Override
    public void formStructure(String substructureName) {
        super.formStructure(substructureName);
        if (!isFormed()) return;
        super.updateActiveBlocks(false);
        refreshEnergyContainer();

        var patternState = getPatternState(substructureName);
        var positions = new HashSet<BlockPos>();
        ICoilType matchedCoil = null;
        IO hatchIO = IO.NONE;
        for (var entry : patternState.getCache().long2ObjectEntrySet()) {
            var state = entry.getValue().getBlockState();
            var pos = BlockPos.of(entry.getLongKey());
            if (state.is(INSULATOR.get())) positions.add(pos);
            var type = PowerlinePredicates.hatchIO(state.getBlock());
            if (type != IO.NONE) {
                if (hatchIO != IO.NONE && hatchIO != type) {
                    patternState.setError(PatternStringError.translatable(
                            "multiblock.gtm_powerlines.pattern.error.mixed_hatches"));
                    invalidateStructure(substructureName);
                    return;
                }
                hatchIO = type;
            }
            for (var coil : GTCEuAPI.HEATING_COILS.entrySet()) {
                if (!state.is(coil.getValue().get())) continue;
                if (matchedCoil != null && matchedCoil != coil.getKey()) {
                    patternState.setError(new CoilMatchingError(
                            pos, matchedCoil, coil.getKey()));
                    invalidateStructure(substructureName);
                    return;
                }
                matchedCoil = coil.getKey();
                break;
            }
        }
        setInsulatorsConnected(insulators, false);
        insulators = Set.copyOf(positions);
        if (matchedCoil != null) coilType = matchedCoil;
        setInsulatorsConnected(insulators, true);

        if (getLevel() instanceof ServerLevel && transferSub == null) {
            transferSub = subscribeServerTick(this::transferTick);
        }
    }

    @Override
    public void invalidateStructure(String substructureName) {
        super.invalidateStructure(substructureName);

        if (transferSub != null) {
            unsubscribe(transferSub);
            transferSub = null;
        }

        setInsulatorsConnected(insulators, false);
        insulators = Collections.emptyList();

        coilType = CoilBlock.CoilType.CUPRONICKEL;
        hatches = Collections.emptyList();
        energyContainer = null;
        networkOutputPerSec = energyOutputPerSec = networkOutput = energyOutput = 0;
        networkInputPerSec = networkInput = receiveCredit = sendCredit = 0;
        receivedTick = -1;
    }

    @Override
    public void onUnload() {
        super.onUnload();
        transferSub = null;
    }

    private void refreshEnergyContainer() {
        List<IEnergyContainer> containers = new ArrayList<>();
        var handlers = getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP);
        if (handlers.isEmpty()) handlers = getCapabilitiesFlat(IO.OUT, EURecipeCapability.CAP);
        for (var handler : handlers) {
            if (handler instanceof IEnergyContainer container) {
                containers.add(container);
            }
        }
        hatches = containers;
        energyContainer = new EnergyContainerList(containers);
    }

    private EnergyContainerList energy() {
        if (energyContainer == null) refreshEnergyContainer();
        return energyContainer;
    }

    public long getLinePower() {
        energy();
        var input = isInput();
        long power = 0;
        for (var hatch : hatches) {
            power += input ? hatch.getInputVoltage() * hatch.getInputAmperage() :
                    hatch.getOutputVoltage() * hatch.getOutputAmperage();
        }
        return power;
    }

    public long getLineVoltage() {
        var power = getLinePower();
        if (power <= 0) return 0;
        if (GTMPowerlinesConfig.INSTANCE.energy.mergeVoltage) {
            return GTValues.V[GTUtil.getFloorTierByVoltage(power)];
        }

        var input = isInput();
        long voltage = 0;
        for (var hatch : hatches) {
            voltage = Math.max(voltage, input ? hatch.getInputVoltage() : hatch.getOutputVoltage());
        }
        return voltage;
    }

    public double getLineAmperage() {
        var voltage = getLineVoltage();
        return voltage <= 0 ? 0 : (double) getLinePower() / voltage;
    }

    public double getCurrentAmperage() {
        var voltage = getLineVoltage();
        if (voltage <= 0) return 0;
        var perTick = (isInput() ? energyOutputPerSec : networkInputPerSec) / 20.0;
        return perTick / voltage;
    }

    @Override
    public int getTier() {
        return GTUtil.getFloorTierByVoltage(getLineVoltage());
    }

    public long getEnergyInput() {
        return networkInputPerSec / 20;
    }

    private long receiveCapacity(long gameTime) {
        var power = getLinePower();
        var max = power + getLineVoltage();
        if (receivedTick != gameTime) {
            var elapsed = receivedTick < 0 ? 1 : Math.max(1, gameTime - receivedTick);
            receivedTick = gameTime;
            receiveCredit = Math.min(max, receiveCredit + power * Math.min(elapsed, 2));
        }
        return Math.max(0, receiveCredit);
    }

    private void onReceived(long energy) {
        receiveCredit -= energy;
        networkInput += energy;
    }

    private void transferTick() {
        if (!(getLevel() instanceof ServerLevel level) || !isFormed() || !level.shouldTickBlocksAt(getBlockPos()))
            return;
        if (hatches.isEmpty()) refreshEnergyContainer();

        if (getOffsetTimer() % 20 == 0) {
            networkOutputPerSec = networkOutput;
            energyOutputPerSec = energyOutput;
            networkInputPerSec = networkInput;
            networkOutput = 0;
            energyOutput = 0;
            networkInput = 0;
        }

        var logic = getRecipeLogic();
        if (!logic.isWorkingEnabled()) {
            logic.setStatus(RecipeLogic.Status.SUSPEND);
            return;
        }

        var active = energyOutputPerSec > 0 || networkInputPerSec > 0;
        logic.setStatus(active ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE);

        if (!isInput()) return;

        if (insulators.isEmpty()) return;

        var source = energy();
        var voltage = getLineVoltage();
        if (voltage <= 0) return;

        // whole amps this tick; the remainder carries over, so 1.5A goes out as 1A, 2A, 1A, 2A...
        sendCredit += getLinePower();
        var ampsThisTick = sendCredit / voltage;
        sendCredit -= ampsThisTick * voltage;

        long ampsLeft = Math.min(ampsThisTick, source.getEnergyStored() / voltage);
        if (ampsLeft <= 0) return;

        var network = PowerlineNetwork.get(level);
        var efficiency = CoilEfficiency.of(coilType);

        for (var insulator : insulators) {
            for (var route : network.getRoutes(insulator).paths()) {
                if (ampsLeft <= 0) break;

                var destPos = network.getTransformerController(route.targetPos());
                if (destPos == null || destPos.equals(getBlockPos())) continue;
                if (!level.isLoaded(destPos) || !level.shouldTickBlocksAt(destPos)) continue;
                var dest = BlockUtils.instanceOf(level, destPos, PowerlineTransformerMachine.class).orElse(null);
                if (dest == null || !dest.isFormed() || !dest.isOutput()) continue;
                if (!dest.getRecipeLogic().isWorkingEnabled()) continue;

                var sink = dest.energy();
                var room = Math.min(sink.getEnergyCanBeInserted(), dest.receiveCapacity(level.getGameTime()));
                if (room <= 0) continue;

                var lossScale = 1f - (efficiency + CoilEfficiency.of(dest.coilType)) / 2f;
                long lossPerAmp = Mth.ceil(route.loss() * lossScale);
                if (lossPerAmp >= voltage) continue;

                var arriving = network.applyVoltageLimit(route, voltage, voltage - lossPerAmp);
                if (arriving <= 0) continue;

                var amps = Math.min(ampsLeft, room / arriving);
                if (amps <= 0) continue;

                var added = sink.changeEnergy(amps * arriving);
                dest.onReceived(added);
                source.changeEnergy(-amps * voltage);
                network.recordFlow(route, voltage, amps, lossScale);

                ampsLeft -= amps;
                networkOutput += added;
                energyOutput += amps * voltage;
            }
        }
    }

    @Override
    public boolean shouldUpdateActiveBlocks() {
        return false;
    }

    @Override
    public void recipeLogicStatusChanged(RecipeLogic.Status oldStatus, RecipeLogic.Status newStatus) {
        for (var trait : getTraitHolder().getTraitsByInterface(IRecipeLogicModifierTrait.class)) {
            trait.recipeLogicStatusChanged(oldStatus, newStatus);
        }
    }

    @Override
    public boolean isRecipeLogicAvailable() {
        return false;
    }

    public boolean isInput() {
        return energy().getInputVoltage() > 0;
    }

    public boolean isOutput() {
        return energy().getOutputVoltage() > 0;
    }

    public IO getIO() {
        if (isInput()) return IO.IN;
        else if (isOutput()) return IO.OUT;
        return IO.NONE;
    }

    private void setInsulatorsConnected(Collection<BlockPos> positions, boolean connected) {
        var level = getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        var network = PowerlineNetwork.get(serverLevel);
        for (var pos : positions) {
            network.setTransformerNode(pos, connected ? getBlockPos() : null);

            var state = level.getBlockState(pos);
            if (!state.is(INSULATOR.get())) continue;

            if (level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity be) {
                be.setController(connected ? getBlockPos() : null);
            }
        }
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        mainWidget.child(getMainTextPanel(syncManager).margin(4, 2));
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        var widgets = new ArrayList<IWidget>();
        widgets.add(GTMultiblockTextUtil.addUnformedWarning(this, syncManager));
        widgets.add(GTMultiblockTextUtil.addWorkingStatusLine(this, syncManager));
        var formed = new BooleanSyncValue(this::isFormed);
        var tier = new IntSyncValue(this::getTier);
        var amperage = new DoubleSyncValue(this::getCurrentAmperage);
        var lineAmperage = new DoubleSyncValue(this::getLineAmperage);
        var efficiency = new IntSyncValue(() -> Mth.floor(100 * CoilEfficiency.of(coilType)));
        var input = new BooleanSyncValue(this::isInput);
        var output = new BooleanSyncValue(this::isOutput);
        var energyOut = new LongSyncValue(() -> energyOutputPerSec / 20);
        var networkOut = new LongSyncValue(() -> networkOutputPerSec / 20);
        var energyIn = new LongSyncValue(this::getEnergyInput);
        var energyLoss = new LongSyncValue(() -> (energyOutputPerSec - networkOutputPerSec) / 20);
        syncManager.syncValue("powerlineFormed", formed);
        syncManager.syncValue("powerlineTier", tier);
        syncManager.syncValue("powerlineAmperage", amperage);
        syncManager.syncValue("powerlineLineAmperage", lineAmperage);
        syncManager.syncValue("powerlineEfficiency", efficiency);
        syncManager.syncValue("powerlineInput", input);
        syncManager.syncValue("powerlineOutput", output);
        syncManager.syncValue("powerlineEnergyOut", energyOut);
        syncManager.syncValue("powerlineNetworkOut", networkOut);
        syncManager.syncValue("powerlineEnergyIn", energyIn);
        syncManager.syncValue("powerlineEnergyLoss", energyLoss);
        widgets.add(Text.dynamic(() -> {
            var line = Component.translatable("gtceu.top.cable_voltage");
            if (amperage.getDoubleValue() > 0) line.append(GTValues.VNF[tier.getIntValue()]).append(" / ");
            return line.append(GTValues.VNF[tier.getIntValue()]);
        }).asWidget().color(0xFFFFFFFF).setEnabledIf(w -> formed.getBoolValue()));
        widgets.add(Text.dynamic(() -> {
            var line = Component.translatable("gtceu.top.cable_amperage");
            if (amperage.getDoubleValue() > 0) line.append(
                    FormattingUtil.DECIMAL_FORMAT_1F.format(amperage.getDoubleValue()) + "A / ");
            return line.append(FormattingUtil.DECIMAL_FORMAT_1F.format(
                    Math.ceil(lineAmperage.getDoubleValue())) + "A");
        }).asWidget().color(0xFFFFFFFF).setEnabledIf(w -> formed.getBoolValue()));
        widgets.add(Text.dynamic(() -> Component.translatable(
                "multiblock.gtm_powerlines.powerline_transformer.powerline_efficiency", efficiency.getIntValue()))
                .asWidget().color(0xFFFFFFFF).setEnabledIf(w -> formed.getBoolValue()));
        widgets.add(Text.dynamic(() -> Component.translatable(
                "multiblock.gtm_powerlines.powerline_transformer.mode").append(
                        Component.translatable(
                                (input.getBoolValue() ? IO.IN : output.getBoolValue() ? IO.OUT : IO.NONE)
                                        .getTooltip())))
                .asWidget().color(0xFFFFFFFF).setEnabledIf(w -> formed.getBoolValue()));
        widgets.add(Text.dynamic(() -> Component.translatable(
                "multiblock.gtm_powerlines.powerline_transformer.energy_output", energyOut.getLongValue()))
                .asWidget().color(0xFFFFFFFF).setEnabledIf(w -> formed.getBoolValue() && input.getBoolValue()));
        widgets.add(Text.dynamic(() -> Component.translatable(
                "multiblock.gtm_powerlines.powerline_transformer.network_output", networkOut.getLongValue()))
                .asWidget().color(0xFFFFFFFF).setEnabledIf(w -> formed.getBoolValue() && input.getBoolValue()));
        widgets.add(Text.dynamic(() -> Component.translatable(
                "multiblock.gtm_powerlines.powerline_transformer.energy_loss",
                energyLoss.getLongValue()))
                .asWidget().color(0xFFFFFFFF).setEnabledIf(w -> formed.getBoolValue() && input.getBoolValue()));
        widgets.add(Text.dynamic(() -> Component.translatable(
                "multiblock.gtm_powerlines.powerline_transformer.energy_input", energyIn.getLongValue()))
                .asWidget().color(0xFFFFFFFF).setEnabledIf(w -> formed.getBoolValue() && !input.getBoolValue()));
        return widgets;
    }
}
