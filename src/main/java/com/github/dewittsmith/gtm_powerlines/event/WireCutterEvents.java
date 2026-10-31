package com.github.dewittsmith.gtm_powerlines.event;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.wire.WireCutSelection;
import com.github.dewittsmith.gtm_powerlines.wire.WireTargeting;

import com.gregtechceu.gtceu.data.recipe.CustomTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID)
public class WireCutterEvents {
    private static final Map<UUID, Long> PLAYER_TICKS = new HashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        if (!event.getItemStack().is(CustomTags.WIRE_CUTTERS)) return;

        var time = level.getGameTime();
        var lastTime = PLAYER_TICKS.put(player.getUUID(), time);
        if (lastTime != null && lastTime == time) return;

        if (level.getBlockEntity(event.getPos()) instanceof PowerlineInsulatorBlockEntity be) {
            if (player.isShiftKeyDown()) {
                refundAll(player, be);
                be.disconnectAll();
                WireCutSelection.clear(player);
                message(player, "All wires removed");
                    message(player, "message.gtm_powerlines.cutter.all_wires_removed");
            } else {
                WireCutSelection.set(player, event.getPos());
                message(player, "Look at a wire and click to cut it");
                    message(player, "message.gtm_powerlines.cutter.look_and_click");
            }
            finish(event);
            return;
        }
        if (tryCut(player, level)) finish(event);
    }

    @SubscribeEvent
    public static void onRightClickAir(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getItemStack().is(CustomTags.WIRE_CUTTERS)) return;
        tryCut(event.getEntity(), event.getLevel());
    }

    private static boolean tryCut(Player player, Level level) {
        var time = level.getGameTime();
        var lastTime = PLAYER_TICKS.put(player.getUUID(), time);
        if (lastTime != null && lastTime == time) return false;

        var sel = WireCutSelection.get(player);
        if (sel == null) return false;

        if (!(level.getBlockEntity(sel.pos()) instanceof PowerlineInsulatorBlockEntity be)) {
            WireCutSelection.clear(player);
            return true;
        }

        var hit = WireTargeting.find(be, player.getEyePosition(), player.getViewVector(1f));
        if (hit == null) {
            WireCutSelection.clear(player);
            message(player, "Cutting cancelled");
                message(player, "message.gtm_powerlines.cutter.cancelled");
            return true;
        }

        BlockPos target = hit.connection().target();
        refund(player, hit.connection());
        be.removeConnectionsTo(target);
        if (level.getBlockEntity(target) instanceof PowerlineInsulatorBlockEntity other) {
            other.removeConnectionsTo(be.getBlockPos());
        }
        message(player, "Wire cut");
            message(player, "message.gtm_powerlines.cutter.wire_cut");
        if (be.getConnections().isEmpty()) WireCutSelection.clear(player);
        return true;
    }

    private static void refund(Player player, PowerlineInsulatorBlockEntity.Connection c) {
        if (player.getAbilities().instabuild) return;
        Item item = ForgeRegistries.ITEMS.getValue(c.wire());
        if (item != null && item != Items.AIR) player.getInventory().placeItemBackInInventory(new ItemStack(item));
    }

    private static void refundAll(Player player, PowerlineInsulatorBlockEntity be) {
        be.getConnections().forEach(c -> refund(player, c));
    }

    private static void message(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }

    private static void finish(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}