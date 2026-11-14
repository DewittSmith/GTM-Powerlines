package com.github.dewittsmith.gtm_powerlines.machines;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
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
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.util.BlockUtils;
import com.github.dewittsmith.gtm_powerlines.util.CoilEfficiency;

import java.util.*;

import javax.annotation.Nullable;

import static com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries.POWERLINE_INSULATOR;

public class PowerlineTransformerMachine extends WorkableMultiblockMachine
                                         implements IFancyUIMachine, IDisplayUIMachine, ITieredMachine {

    private ICoilType coilType = CoilBlock.CoilType.CUPRONICKEL;

    /** EU per second that arrived at receiving transformers (after line loss). */
    private long networkOutputPerSec;

    /** EU per second drained from this transformer's energy hatches. */
    private long energyOutputPerSec;

    private long networkOutput;
    private long energyOutput;

    private List<IEnergyContainer> hatches = Collections.emptyList();
    private @Nullable EnergyContainerList energyContainer;
    private Collection<BlockPos> insulators = Collections.emptyList();

    private @Nullable TickableSubscription transferSub;

    /** Why the last transfer tick did (not) move energy; shown in the controller UI. */
    public enum TransferStatus {

        IDLE,
        NO_ENERGY,
        NO_INSULATORS,
        NO_RECEIVER,
        RECEIVER_UNLOADED,
        RECEIVER_FULL,
        LOSS_TOO_HIGH,
        TRANSFERRING;

        public String translationKey() {
            return "multiblock.gtm_powerlines.powerline_transformer.status." + name().toLowerCase(Locale.ROOT);
        }
    }

    private TransferStatus status = TransferStatus.IDLE;
    private int receivers;

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

    public TransferStatus getStatus() {
        return status;
    }

    /** Number of step-down transformers that received energy during the last tick. */
    public int getReceivers() {
        return receivers;
    }

    public int getInsulatorCount() {
        return insulators.size();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        refreshEnergyContainer();

        // copy (and dedupe): the match context list belongs to the pattern matcher
        insulators = Set.copyOf(getMultiblockState().getMatchContext().getOrDefault("insulators",
                Collections.<BlockPos>emptyList()));
        setInsulatorsConnected(insulators, true);

        var type = getMultiblockState().getMatchContext().get("CoilType");
        if (type instanceof ICoilType coil) {
            this.coilType = coil;
        }

        // always tick while formed: the hatch setup decides each tick whether this is the sending side
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
        status = TransferStatus.IDLE;
        receivers = 0;
    }

    @Override
    public void onUnload() {
        super.onUnload();
        // MetaMachine drops all tick subscriptions on unload
        transferSub = null;
    }

    private void refreshEnergyContainer() {
        List<IEnergyContainer> containers = new ArrayList<>();
        var handlers = getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP);
        if (handlers.isEmpty()) handlers = getCapabilitiesFlat(IO.OUT, EURecipeCapability.CAP);
        for (IRecipeHandler<?> handler : handlers) {
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

    /**
     * Voltage of a single energy hatch. Unlike {@link EnergyContainerList#getInputVoltage()} this does not merge
     * several hatches into a higher "virtual" voltage, so the line tier always equals the hatch tier.
     */
    public long getLineVoltage() {
        energy();
        long voltage = 0;
        for (var hatch : hatches) {
            voltage = Math.max(voltage, isInput() ? hatch.getInputVoltage() : hatch.getOutputVoltage());
        }
        return voltage;
    }

    /** Total amperage of all hatches running at {@link #getLineVoltage()}. */
    public long getLineAmperage() {
        long voltage = getLineVoltage();
        long amperage = 0;
        for (var hatch : hatches) {
            if (isInput() ? hatch.getInputVoltage() == voltage : hatch.getOutputVoltage() == voltage) {
                amperage += isInput() ? hatch.getInputAmperage() : hatch.getOutputAmperage();
            }
        }
        return amperage;
    }

    @Override
    public int getTier() {
        return GTUtil.getFloorTierByVoltage(getLineVoltage());
    }

    public long getEnergyInput() {
        return energy().getInputPerSec() / 20;
    }

    /**
     * Step-up side: pushes energy from the input hatches, over the powerline, into every reachable step-down
     * transformer. Loss per block is reduced by the average coil efficiency of both transformers.
     */
    private void transferTick() {
        if (!(getLevel() instanceof ServerLevel level) || !isFormed() || !level.shouldTickBlocksAt(getPos())) return;
        if (hatches.isEmpty()) refreshEnergyContainer();
        if (!isInput()) {
            status = TransferStatus.IDLE;
            return;
        }

        if (getOffsetTimer() % 20 == 0) {
            networkOutputPerSec = networkOutput;
            energyOutputPerSec = energyOutput;
            networkOutput = 0;
            energyOutput = 0;
        }

        if (insulators.isEmpty()) {
            status = TransferStatus.NO_INSULATORS;
            return;
        }

        var source = energy();
        long voltage = getLineVoltage();
        long ampsLeft = voltage <= 0 ? 0 : Math.min(getLineAmperage(), source.getEnergyStored() / voltage);
        if (ampsLeft <= 0) {
            status = TransferStatus.NO_ENERGY;
            return;
        }

        var network = PowerlineNetwork.get(level);
        var efficiency = CoilEfficiency.of(coilType);

        // the most informative reason something was skipped, in case nothing gets sent
        var blocked = TransferStatus.NO_RECEIVER;
        var served = new HashSet<BlockPos>();

        for (var insulator : insulators) {
            for (var route : network.getRoutes(insulator).paths()) {
                if (ampsLeft <= 0) break;

                var destPos = network.getTransformerController(route.targetPos());
                if (destPos == null || destPos.equals(getPos())) continue;
                // the receiver must be in a ticking (properly loaded) chunk, not just a border/lazy one
                if (!level.isLoaded(destPos) || !level.shouldTickBlocksAt(destPos)) {
                    blocked = worse(blocked, TransferStatus.RECEIVER_UNLOADED);
                    continue;
                }
                var dest = BlockUtils.instanceOf(level, destPos, PowerlineTransformerMachine.class).orElse(null);
                if (dest == null || !dest.isFormed() || !dest.isOutput()) continue;

                var sink = dest.energy();
                long room = sink.getEnergyCanBeInserted();
                if (room <= 0) {
                    blocked = worse(blocked, TransferStatus.RECEIVER_FULL);
                    continue;
                }

                var lossScale = 1f - (efficiency + CoilEfficiency.of(dest.coilType)) / 2f;
                long lossPerAmp = Mth.ceil(route.loss() * lossScale);
                if (lossPerAmp >= voltage) {
                    blocked = worse(blocked, TransferStatus.LOSS_TOO_HIGH);
                    continue;
                }

                long arriving = network.applyVoltageLimit(route, voltage, voltage - lossPerAmp);
                if (arriving <= 0) continue;

                long amps = Math.min(ampsLeft, room / arriving);
                if (amps <= 0) {
                    blocked = worse(blocked, TransferStatus.RECEIVER_FULL);
                    continue;
                }

                long added = sink.changeEnergy(amps * arriving);
                source.changeEnergy(-amps * voltage);
                network.recordFlow(route, voltage, amps, lossScale);

                ampsLeft -= amps;
                networkOutput += added;
                energyOutput += amps * voltage;
                served.add(destPos);
            }
        }

        receivers = served.size();
        status = served.isEmpty() ? blocked : TransferStatus.TRANSFERRING;
    }

    private static TransferStatus worse(TransferStatus current, TransferStatus candidate) {
        return candidate.ordinal() > current.ordinal() ? candidate : current;
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
        Level level = getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        var network = PowerlineNetwork.get(serverLevel);
        for (BlockPos pos : positions) {
            // always update the network, even if the insulator block is already gone
            network.setTransformerNode(pos, connected ? getPos() : null);

            BlockState state = level.getBlockState(pos);
            if (!state.is(POWERLINE_INSULATOR.get())) continue;

            if (level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity be) {
                be.setController(connected ? getPos() : null);
            }
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
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
        for (IMultiPart part : getParts()) {
            part.attachFancyTooltipsToController(this, tooltipsPanel);
        }
    }
}
