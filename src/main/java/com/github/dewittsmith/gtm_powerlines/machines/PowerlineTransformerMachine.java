package com.github.dewittsmith.gtm_powerlines.machines;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.util.BlockUtils;
import com.github.dewittsmith.gtm_powerlines.util.CoilEfficiency;

import java.util.*;

import javax.annotation.Nullable;

import static com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries.INSULATOR;

public class PowerlineTransformerMachine extends WorkableMultiblockMachine
                                         implements IFancyUIMachine, IDisplayUIMachine, ITieredMachine {

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

    public PowerlineTransformerMachine(IMachineBlockEntity holder) {
        super(holder);
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
    public void onStructureFormed() {
        super.onStructureFormed();
        refreshEnergyContainer();

        insulators = Set.copyOf(getMultiblockState().getMatchContext().getOrDefault("insulators",
                Collections.emptyList()));
        setInsulatorsConnected(insulators, true);

        var type = getMultiblockState().getMatchContext().get("CoilType");
        if (type instanceof ICoilType coil) {
            this.coilType = coil;
        }

        if (getLevel() instanceof ServerLevel && transferSub == null) {
            transferSub = subscribeServerTick(this::transferTick);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();

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
        if (!(getLevel() instanceof ServerLevel level) || !isFormed() || !level.shouldTickBlocksAt(getPos())) return;
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
                if (destPos == null || destPos.equals(getPos())) continue;
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
            network.setTransformerNode(pos, connected ? getPos() : null);

            var state = level.getBlockState(pos);
            if (!state.is(INSULATOR.get())) continue;

            if (level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity be) {
                be.setController(connected ? getPos() : null);
            }
        }
    }

    @Override
    public void notifyStatusChanged(RecipeLogic.Status oldStatus, RecipeLogic.Status newStatus) {
        // super.notifyStatusChanged(oldStatus, newStatus);
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        var logic = getRecipeLogic();
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(logic.isWorkingEnabled(), logic.isActive())
                .addWorkingStatusLine();
        getDefinition().getAdditionalDisplay().accept(this, textList);
        IDisplayUIMachine.super.addDisplayText(textList);
    }

    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 182 + 8, 117 + 8);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 117).setBackground(getScreenTexture())
                .addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText)
                        .textSupplier(this.getLevel().isClientSide ? null : this::addDisplayText)
                        .setMaxWidthLimit(200)
                        .clickHandler(this::handleDisplayClick)));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(198, 208, this, entityPlayer).widget(new FancyMachineUIWidget(this, 198, 208));
    }

    @Override
    public List<IFancyUIProvider> getSubTabs() {
        return getParts().stream().filter(Objects::nonNull).map(IFancyUIProvider.class::cast).toList();
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        IFancyUIMachine.super.attachConfigurators(configuratorPanel);
    }

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        for (var part : getParts()) {
            part.attachFancyTooltipsToController(this, tooltipsPanel);
        }
    }
}
