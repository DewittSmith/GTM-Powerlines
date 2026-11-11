package com.github.dewittsmith.gtm_powerlines.event;

import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.wire.WireCutSelection;
import com.github.dewittsmith.gtm_powerlines.wire.WireTargeting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID)
public class WireCutterEvents {

    private static final Map<UUID, Long> HANDLED = new HashMap<>();

    private static boolean alreadyHandled(Player p, Level l) {
        Long t = HANDLED.get(p.getUUID());
        return t != null && t == l.getGameTime();
    }

    private static void markHandled(Player p, Level l) {
        HANDLED.put(p.getUUID(), l.getGameTime());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        if (!event.getItemStack().is(CustomTags.WIRE_CUTTERS)) return;
        if (alreadyHandled(player, level)) return;

        if (level.getBlockEntity(event.getPos()) instanceof PowerlineInsulatorBlockEntity be) {
            markHandled(player, level);
            if (player.isShiftKeyDown()) {
                var removed = be.disconnectAll();
                removed.forEach(e -> refund(player, e));
                WireCutSelection.clear(player);
                message(player, "message.gtm_powerlines.cutter.all_wires_removed");
            } else {
                WireCutSelection.set(player, event.getPos());
                message(player, "message.gtm_powerlines.cutter.look_and_click");
            }
            finish(event);
            return;
        }
        if (tryCut(player, level)) finish(event);
    }

    @SubscribeEvent
    public static void onRightClickAir(PlayerInteractEvent.RightClickItem event) {
        Level level = event.getLevel();
        if (level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        if (!event.getItemStack().is(CustomTags.WIRE_CUTTERS)) return;
        if (alreadyHandled(player, level)) return;
        tryCut(player, level);
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getSlot() != EquipmentSlot.MAINHAND) return;
        var sel = WireCutSelection.get(player);
        if (sel == null) return;

        WireCutSelection.clear(player);
        message(player, "message.gtm_powerlines.cutter.cancelled");
    }

    private static boolean tryCut(Player player, Level level) {
        var sel = WireCutSelection.get(player);
        if (sel == null) return false;
        markHandled(player, level);

        if (!(level.getBlockEntity(sel.pos()) instanceof PowerlineInsulatorBlockEntity be)) {
            WireCutSelection.clear(player);
            return true;
        }

        var hit = WireTargeting.find(be.getBlockPos(), be.getConnections(), player.getEyePosition(),
                player.getViewVector(1f));
        if (hit == null) {
            WireCutSelection.clear(player);
            message(player, "message.gtm_powerlines.cutter.cancelled");
            return true;
        }

        var edge = hit.edge();
        BlockPos target = edge.other(be.getBlockPos());
        if (be.disconnectFrom(target)) {
            refund(player, edge);
            message(player, "message.gtm_powerlines.cutter.wire_cut");
        }
        if (be.getConnections().isEmpty()) WireCutSelection.clear(player);
        return true;
    }

    private static void refund(Player player, PowerlineGraph.Edge edge) {
        if (player.getAbilities().instabuild) return;
        Item item = ForgeRegistries.ITEMS.getValue(edge.wire());
        if (item != null) player.getInventory().placeItemBackInInventory(new ItemStack(item, edge.wireCount()));
    }

    private static void message(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }

    private static void finish(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
