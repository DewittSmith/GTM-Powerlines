package com.github.dewittsmith.gtm_powerlines.compat.jade;

import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class PowerlineJadePlugin implements IWailaPlugin {

    private final PowerlineInsulatorProvider insulatorProvider = new PowerlineInsulatorProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(insulatorProvider, PowerlineInsulatorBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(insulatorProvider, PowerlineInsulatorBlock.class);
    }
}
