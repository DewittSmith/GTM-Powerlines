package com.github.dewittsmith.gtm_powerlines.events;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.GTMPowerlinesMachines;
import com.github.dewittsmith.gtm_powerlines.PowerlineWireRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GTMPowerlinesMachines.POWERLINE_INSULATOR_BLOCK_ENTITY.get(), PowerlineWireRenderer::new);
    }
}