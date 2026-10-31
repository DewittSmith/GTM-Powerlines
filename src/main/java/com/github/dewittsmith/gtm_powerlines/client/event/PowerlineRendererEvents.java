package com.github.dewittsmith.gtm_powerlines.client.event;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.client.renderer.PowerlineWireRenderer;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PowerlineRendererEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GTMPowerlinesRegistries.POWERLINE_INSULATOR_BLOCK_ENTITY.get(), PowerlineWireRenderer::new);
    }
}