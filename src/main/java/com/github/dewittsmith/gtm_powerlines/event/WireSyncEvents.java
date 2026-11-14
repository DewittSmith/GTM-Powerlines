package com.github.dewittsmith.gtm_powerlines.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.WireFullSyncPacket;
import com.github.dewittsmith.gtm_powerlines.wire.WireAttachment;

import java.util.List;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID)
public final class WireSyncEvents {

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) fullSync(p);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) fullSync(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) fullSync(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            WireAttachment.clear(p);
        }
    }

    private static void fullSync(ServerPlayer p) {
        WireAttachment.clear(p);
        var network = PowerlineNetwork.get(p.serverLevel());
        Net.sendToPlayer(p, new WireFullSyncPacket(List.copyOf(network.getGraph().all())));
    }
}
