package com.github.dewittsmith.gtm_powerlines.compat.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class PowerlineJadePlugin implements IWailaPlugin {

    private final PowerlineInsulatorProvider insulatorProvider = new PowerlineInsulatorProvider();
    private final PowerlineTowerProvider powerlineTowerProvider = new PowerlineTowerProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(insulatorProvider, PowerlineInsulatorBlockEntity.class);
        registration.registerBlockDataProvider(powerlineTowerProvider, MetaMachineBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(insulatorProvider, PowerlineInsulatorBlock.class);
        registration.registerBlockComponent(powerlineTowerProvider,
                GTMPowerlinesRegistries.POWERLINE_TOWER.getBlock().getClass());
    }
}
