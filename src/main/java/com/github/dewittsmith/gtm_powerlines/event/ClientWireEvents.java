package com.github.dewittsmith.gtm_powerlines.event;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;
import com.github.dewittsmith.gtm_powerlines.wire.WireAttachment;
import com.github.dewittsmith.gtm_powerlines.wire.WireCutSelection;
import com.github.dewittsmith.gtm_powerlines.wire.WireInfo;
import com.gregtechceu.gtceu.api.GTValues;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, value = Dist.CLIENT)
public final class ClientWireEvents {

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientWires.clear();
        WireAttachment.setClient(null);
        WireCutSelection.setClient(null);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (!stack.is(GTMPowerlinesRegistries.POWERLINE_WIRE)) return;

        var info = WireInfo.of(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        if (info.isEmpty()) return;

        var tooltip = event.getToolTip();
        tooltip.add(Component.translatable("gtceu.cable.voltage", GTValues.VEX[info.get().tier()], GTValues.VNF[info.get().tier()]));
        tooltip.add(Component.translatable("gtceu.cable.loss_per_block", Mth.ceil(info.get().lossPerBlock())));
    }
}
