package com.github.dewittsmith.gtm_powerlines.event;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.ObstructionHighlightPacket;
import com.github.dewittsmith.gtm_powerlines.wire.WireObstruction;

import java.util.ArrayList;
import java.util.List;

/** Stops blocks from being placed through an existing cable (unless tagged {@code wire_passthrough}). */
@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID)
public final class WireObstructionEvents {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!WireObstruction.enabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var state = event.getPlacedBlock();
        var pos = event.getPos();
        if (WireObstruction.ignored(state)) return;

        var graph = PowerlineNetwork.get(level).getGraph();
        var blocking = new ArrayList<BlockPos>();
        for (var edge : graph.atChunk(new ChunkPos(pos))) {
            if (edge.stats() == null || !WireObstruction.blocks(level, edge, pos, state)) continue;
            blocking.add(edge.a());
            blocking.add(edge.b());
        }
        if (blocking.isEmpty()) return;

        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.gtm_powerlines.wire.in_the_way"), true);
            Net.sendToPlayer(player, new ObstructionHighlightPacket(List.of(), blocking));
        }
    }
}
