package com.github.dewittsmith.gtm_powerlines.machines;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.network.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.util.BlockUtils;
import com.github.dewittsmith.gtm_powerlines.util.CoilEfficiency;
import com.github.dewittsmith.gtm_powerlines.wire.IWireMachine;
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
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.*;

import static com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries.POWERLINE_INSULATOR;

public class PowerlineTransformerMachine extends WorkableMultiblockMachine implements IFancyUIMachine, IDisplayUIMachine, IWireMachine, ITieredMachine {

    @Getter
    private ICoilType coilType = CoilBlock.CoilType.CUPRONICKEL;

    @Getter
    private long networkOutputPerSec;

    @Getter
    private long energyOutputPerSec;

    private EnergyContainerList energyContainer;
    private Collection<BlockPos> insulators = Collections.emptyList();

    private @Nullable TickableSubscription transferSub;

    public PowerlineTransformerMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        energyContainer = getEnergyContainer();

        insulators = getMultiblockState().getMatchContext().getOrDefault("insulators", Collections.emptyList());
        setInsulatorsConnected(insulators, true);

        var type = getMultiblockState().getMatchContext().get("CoilType");
        if (type instanceof ICoilType coil) {
            this.coilType = coil;
        }

        if (!isRemote() && !isOutput() && transferSub == null) {
            transferSub = subscribeServerTick(this::transferTick);
        }

        if (getLevel() instanceof ServerLevel level) {
            for (var insulator : insulators) {
                PowerlineNetwork.get(level).rebuildTransferEdges(level, insulator);
            }
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        if (transferSub != null) {
            unsubscribe(transferSub);
            transferSub = null;
        }

        energyContainer = null;
        setInsulatorsConnected(insulators, false);
        insulators = Collections.emptyList();
    }

    @Override
    public int getTier() {
        if (energyContainer == null) energyContainer = getEnergyContainer();
        if (isInput()) return GTUtil.getFloorTierByVoltage(energyContainer.getInputVoltage());
        else return GTUtil.getFloorTierByVoltage(energyContainer.getOutputVoltage());
    }

    public long getEnergyInput() {
        if (energyContainer == null) energyContainer = getEnergyContainer();
        return energyContainer.getInputPerSec() / 20;
    }

    private EnergyContainerList getEnergyContainer() {
        List<IEnergyContainer> containers = new ArrayList<>();
        var handlers = getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP);
        if (handlers.isEmpty()) handlers = getCapabilitiesFlat(IO.OUT, EURecipeCapability.CAP);
        for (IRecipeHandler<?> handler : handlers) {
            if (handler instanceof IEnergyContainer container) {
                containers.add(container);
            }
        }
        return new EnergyContainerList(containers);
    }

    private long networkOutput;
    private long energyOutput;

    private void transferTick() {
        if (!(getLevel() instanceof ServerLevel level) || isOutput()) return;
        if (energyContainer.getEnergyStored() == 0) return;

        if (getOffsetTimer() % 20 == 0) {
            networkOutputPerSec = networkOutput;
            energyOutputPerSec = energyOutput;

            networkOutput = 0;
            energyOutput = 0;
        }

        var efficiency = CoilEfficiency.of(coilType);
        var network = PowerlineNetwork.get(level);
        for (var insulator : insulators) {
            for (var edge : network.transferAt(insulator)) {
                var stored = energyContainer.getEnergyStored();
                if (stored == 0) return;
                var otherOpt = BlockUtils.instanceOf(level, edge.b(), PowerlineInsulatorBlockEntity.class);
                if (otherOpt.isEmpty()) continue;
                var other = BlockUtils.instanceOf(level, otherOpt.get().getController(), PowerlineTransformerMachine.class);
                if (other.isEmpty()) continue;
                var otherContainer = other.get().energyContainer;
                if (otherContainer.getEnergyCanBeInserted() <= 0) continue;

                var otherEfficiency = CoilEfficiency.of(other.get().coilType);
                var penalty = 1f - (efficiency + otherEfficiency) / 2;
                var canTransfer = stored - Mth.ceil(edge.totalLoss() * penalty);
                if (canTransfer <= 0) continue;

                var added = otherContainer.addEnergy(canTransfer);
                var toRemove = Mth.ceil(stored * ((float) added / canTransfer));
                energyContainer.removeEnergy(toRemove);

                networkOutput += added;
                energyOutput += toRemove;
            }
        }
    }

    public boolean isInput() {
        return energyContainer != null && energyContainer.getInputVoltage() > 0;
    }

    public boolean isOutput() {
        return energyContainer != null && energyContainer.getOutputVoltage() > 0;
    }

    private void setInsulatorsConnected(Collection<BlockPos> positions, boolean connected) {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;
        for (BlockPos pos : positions) {
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

    @Override
    public int getMaxConnections() {
        return 1;
    }

    @Override
    public float getWireDistanceFor(BlockPos block) {
        return 16;
    }
}
