package com.github.dewittsmith.gtm_powerlines.event;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.item.MultimeterItem;
import com.github.dewittsmith.gtm_powerlines.wire.*;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, value = Dist.CLIENT)
public final class ClientWireEvents {

    private static int lastSelectedSlot = -1;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var player = Minecraft.getInstance().player;
        if (player == null) {
            lastSelectedSlot = -1;
            return;
        }
        int slot = player.getInventory().selected;
        boolean switched = lastSelectedSlot != -1 && slot != lastSelectedSlot;
        lastSelectedSlot = slot;

        if (MultimeterHighlight.get() == null) return;
        boolean holding = player.getMainHandItem().getItem() instanceof MultimeterItem ||
                player.getOffhandItem().getItem() instanceof MultimeterItem;
        if (switched || !holding) MultimeterHighlight.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientWires.clear();
        WireAttachment.setClient(null);
        MultimeterHighlight.clear();
        ObstructionHighlight.clear();
        ClientWireStats.clear();
    }
}
