package com.github.dewittsmith.gtm_powerlines.machines;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.wire.IWireMachine;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.*;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries.POWERLINE_INSULATOR;

public class PowerlineTransformerMachine extends WorkableMultiblockMachine implements IFancyUIMachine,
        IDisplayUIMachine, IWireMachine, ITieredMachine {

    @Getter
    private ICoilType coilType = CoilBlock.CoilType.CUPRONICKEL;

    @Getter
    private int tier;

    @Getter
    private long maxVoltage;
    private IO energyIO = null;
    private Collection<BlockPos> insulators = Collections.emptyList();

    public PowerlineTransformerMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        recalculateEnergy();
        tier = GTUtil.getFloorTierByVoltage(maxVoltage);

        insulators = getMultiblockState().getMatchContext().getOrDefault("insulators", Collections.emptyList());
        setInsulatorsConnected(insulators, true);

        var type = getMultiblockState().getMatchContext().get("CoilType");
        if (type instanceof ICoilType coil) {
            this.coilType = coil;
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        maxVoltage = 0;
        energyIO = null;
        tier = 0;

        setInsulatorsConnected(insulators, false);
        insulators = Collections.emptyList();
    }

    private void recalculateEnergy() {
        long total = 0;
        boolean hasIn = false, hasOut = false;

        for (IMultiPart part : getParts()) {
            for (MachineTrait trait : part.self().getTraits()) {
                if (!(trait instanceof NotifiableEnergyContainer container)) continue;

                if (container.getHandlerIO() == IO.IN) {
                    hasIn = true;
                    total += container.getInputVoltage() * container.getInputAmperage();
                } else if (container.getHandlerIO() == IO.OUT) {
                    hasOut = true;
                    total += container.getOutputVoltage() * container.getOutputAmperage();
                }
            }
        }

        boolean mixed = hasIn && hasOut;
        energyIO = mixed ? null : hasIn ? IO.IN : hasOut ? IO.OUT : null;
        maxVoltage = mixed ? 0 : total;
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
