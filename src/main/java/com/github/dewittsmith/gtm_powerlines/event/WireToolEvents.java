package com.github.dewittsmith.gtm_powerlines.event;

import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;
import com.github.dewittsmith.gtm_powerlines.wire.WireTargeting;
import com.github.dewittsmith.gtm_powerlines.wire.WireTools;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID)
public final class WireToolEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var stack = event.getItemStack();
        if (!WireTools.targetsWires(stack)) return;

        var player = event.getEntity();
        var level = event.getLevel();

        var hit = WireTargeting.pick(graph(level), player, 1f);
        if (hit != null) {
            if (!level.isClientSide) useOnWire(player, event.getHand(), stack, hit);
            finish(event);
            return;
        }

        if (WireTools.isCutter(stack) && player.isShiftKeyDown() &&
                level.getBlockEntity(event.getPos()) instanceof PowerlineInsulatorBlockEntity be) {
            if (!level.isClientSide) {
                var removed = be.disconnectAll();
                removed.forEach(edge -> refund(player, edge));
                if (!removed.isEmpty()) damageCutter(player, stack);
                message(player, "message.gtm_powerlines.cutter.all_wires_removed");
            }
            finish(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        var stack = event.getItemStack();
        if (!WireTools.targetsWires(stack)) return;

        var player = event.getEntity();
        var level = event.getLevel();
        var hit = WireTargeting.pick(graph(level), player, 1f);
        if (hit == null) return;

        if (!level.isClientSide) useOnWire(player, event.getHand(), stack, hit);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static PowerlineGraph graph(Level level) {
        return level instanceof ServerLevel serverLevel ? PowerlineNetwork.get(serverLevel).getGraph() :
                ClientWires.graph();
    }

    private static void useOnWire(Player player, InteractionHand hand, ItemStack stack, WireTargeting.Hit hit) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var network = PowerlineNetwork.get(level);
        var edge = hit.edge();

        if (WireTools.isCutter(stack)) {
            if (network.disconnect(edge.a(), edge.b())) {
                refund(player, edge);
                damageCutter(player, stack);
                message(player, "message.gtm_powerlines.cutter.wire_cut");
            }
            return;
        }

        var color = WireTools.sprayColor(stack);
        if (color.isPresent() && network.repaint(edge, color.getAsInt())) {
            WireTools.useSprayCharge(player, hand, stack);
            GTSoundEntries.SPRAY_CAN_TOOL.play(level, null, hit.point(), 1f, 1f);
        }
    }

    private static void damageCutter(Player player, ItemStack stack) {
        ToolHelper.damageItem(stack, player);
        if (player instanceof ServerPlayer serverPlayer) ToolHelper.playToolSound(GTToolType.WIRE_CUTTER, serverPlayer);
    }

    private static void refund(Player player, PowerlineGraph.Edge edge) {
        if (player.getAbilities().instabuild) return;
        var item = ForgeRegistries.ITEMS.getValue(edge.wire());
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
