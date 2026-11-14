package com.github.dewittsmith.gtm_powerlines.renderer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PowerlineColorHandlers {

    /** Faces with {@code "tintindex": 0} in the insulator model are the ceramic discs. */
    private static final int DISC_TINT = 0;

    /**
     * Colour of unpainted discs. The disc pixels in the texture are greyscale (each divided by this colour), so tinting
     * them with it restores the original ceramic look.
     */
    public static final int DEFAULT_DISC_COLOR = 0xE3826C;

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != DISC_TINT) return 0xFFFFFF;
            if (level != null && pos != null &&
                    level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity insulator &&
                    insulator.isPainted()) {
                return insulator.getPaintingColor() & 0xFFFFFF;
            }
            return DEFAULT_DISC_COLOR;
        }, GTMPowerlinesRegistries.POWERLINE_INSULATOR.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == DISC_TINT ? DEFAULT_DISC_COLOR : 0xFFFFFF,
                GTMPowerlinesRegistries.POWERLINE_INSULATOR.get());
    }
}
