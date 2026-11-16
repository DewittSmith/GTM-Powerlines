package com.github.dewittsmith.gtm_powerlines.event;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.network.ObstructionHighlightPacket;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;
import com.github.dewittsmith.gtm_powerlines.util.ItemUtils;
import com.github.dewittsmith.gtm_powerlines.wire.WireAttachment;
import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import com.github.dewittsmith.gtm_powerlines.wire.WireObstruction;
import com.github.dewittsmith.gtm_powerlines.wire.WireStats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID)
public class PowerlineWireEvents {

    private static final Map<UUID, Long> PLAYER_TICKS = new HashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        if (level.isClientSide()) return;

        var player = event.getEntity();
        var attach = WireAttachment.get(player);
        var dragging = attach != null && attach.dimension().equals(level.dimension());

        InteractionHand hand;
        if (dragging) {
            hand = handHolding(player, attach.wire());
            if (hand == null) return;
        } else {
            hand = event.getHand();
            if (WireStats.of(event.getItemStack().getItem()).isEmpty()) return;
        }
        var held = player.getItemInHand(hand);
        var wireItem = held.getItem();
        var wireInfo = WireStats.of(wireItem).orElse(null);
        if (wireInfo == null) return;

        var time = level.getGameTime();
        var lastTime = PLAYER_TICKS.put(player.getUUID(), time);
        if (lastTime != null && lastTime == time) {
            if (dragging) finish(event);
            return;
        }

        var pos = event.getPos();
        var clickedBe = level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity be ? be : null;

        if (clickedBe == null) {
            if (dragging) {
                if (!placeInsulatorAndConnect(event, player, hand, held, wireInfo, attach)) {
                    WireAttachment.clear(player);
                    message(player, "message.gtm_powerlines.wire.cancelled");
                }
                finish(event);
            }
            return;
        }

        if (!dragging) {
            if (!clickedBe.hasConnectionCapacity()) {
                message(player, "message.gtm_powerlines.wire.too_many_connections");
                finish(event);
                return;
            }

            var tier = clickedBe.getWireTier();
            if (tier != -1 && wireInfo.tier() != -1 && tier != wireInfo.tier()) {
                message(player, "message.gtm_powerlines.wire.incompatible_tier", GTValues.VNF[tier],
                        GTValues.VNF[wireInfo.tier()]);
                finish(event);
                return;
            }

            WireAttachment.set(player, level, pos, wireItem, wireInfo);
            message(player, "message.gtm_powerlines.wire.attached_click_another");
            finish(event);
            return;
        }

        if (attach.pos().equals(pos)) {
            WireAttachment.clear(player);
            message(player, "message.gtm_powerlines.wire.cancelled");
            finish(event);
            return;
        }

        tryConnect(player, attach, clickedBe, held, wireInfo);
        finish(event);
    }

    private static boolean placeInsulatorAndConnect(PlayerInteractEvent.RightClickBlock event, Player player,
                                                    InteractionHand wireHand, ItemStack held, WireStats wireInfo,
                                                    WireAttachment.Attach attach) {
        var insulatorItem = GTMPowerlinesRegistries.POWERLINE_INSULATOR.get().asItem();
        var stack = findInsulator(player);
        if (stack == null) {
            if (!player.getAbilities().instabuild) return false;
            stack = new ItemStack(insulatorItem);
        }
        if (!(insulatorItem instanceof BlockItem blockItem)) return false;

        var context = new BlockPlaceContext(player, wireHand, stack, event.getHitVec());
        var result = blockItem.place(context);
        if (!result.consumesAction()) {
            message(player, "message.gtm_powerlines.wire.cannot_place_insulator");
            return true;
        }

        var level = event.getLevel();
        var placedPos = context.getClickedPos();
        if (!(level.getBlockEntity(placedPos) instanceof PowerlineInsulatorBlockEntity placed)) return true;

        if (!tryConnect(player, attach, placed, held, wireInfo)) {
            level.removeBlock(placedPos, false);
            if (!player.getAbilities().instabuild) {
                player.getInventory().placeItemBackInInventory(new ItemStack(insulatorItem));
            }
        }
        return true;
    }

    private static @Nullable ItemStack findInsulator(Player player) {
        var item = GTMPowerlinesRegistries.POWERLINE_INSULATOR.get().asItem();
        var inventory = player.getInventory();
        for (var stack : inventory.offhand) if (stack.is(item)) return stack;
        for (var stack : inventory.items) if (stack.is(item)) return stack;
        return null;
    }

    private static boolean tryConnect(Player player, WireAttachment.Attach attach,
                                      PowerlineInsulatorBlockEntity clickedBe, ItemStack held, WireStats wireInfo) {
        var level = clickedBe.getLevel();
        var pos = clickedBe.getBlockPos();
        var wireItem = held.getItem();
        var wire = ForgeRegistries.ITEMS.getKey(wireItem);
        if (level == null) return false;

        if (!(level.getBlockEntity(attach.pos()) instanceof PowerlineInsulatorBlockEntity fromBe)) {
            WireAttachment.clear(player);
            message(player, "message.gtm_powerlines.wire.start_insulator_gone");
            return false;
        }

        var fromController = fromBe.getController();
        var toController = clickedBe.getController();

        if (fromController != null && fromController.equals(toController)) {
            var tier = clickedBe.getWireTier();
            if (tier != -1 && tier != wireInfo.tier()) {
                message(player, "message.gtm_powerlines.wire.incompatible_tier", GTValues.VNF[tier],
                        GTValues.VNF[wireInfo.tier()]);
                return false;
            }

            WireAttachment.set(player, level, pos, wireItem, wireInfo);
            message(player, "message.gtm_powerlines.wire.attached_click_another");
            return true;
        }

        var flatDistance = Math.sqrt(attach.pos().atY(0).distSqr(pos.atY(0)));
        var maxDistance = clickedBe.maxConnectionRange() + fromBe.maxConnectionRange();
        if (flatDistance > maxDistance) {
            message(player, "message.gtm_powerlines.wire.too_long", Mth.ceil(flatDistance), Mth.floor(maxDistance));
            return false;
        }

        if (WireObstruction.enabled()) {
            var fromDir = fromBe.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION);
            var toDir = clickedBe.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION);
            var blocked = WireObstruction.find(level,
                    WireGeometry.getAttachmentPoint(attach.pos(), fromDir, wireInfo),
                    WireGeometry.getAttachmentPoint(pos, toDir, wireInfo));
            if (!blocked.isEmpty()) {
                message(player, "message.gtm_powerlines.wire.blocked");
                if (player instanceof ServerPlayer serverPlayer) {
                    Net.sendToPlayer(serverPlayer, new ObstructionHighlightPacket(blocked, List.of()));
                }
                return false;
            }
        }

        var count = PowerlineGraph.Edge.wireCount(attach.pos(),
                fromBe.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION), pos,
                clickedBe.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION));
        var haveCount = ItemUtils.countAvailable(player, held);
        if (haveCount < count) {
            message(player, "message.gtm_powerlines.wire.not_enough", haveCount, count);
            return false;
        }

        var connectResult = fromBe.connect(pos, wire);
        switch (connectResult.status()) {
            case SUCCESS -> {
                ItemUtils.consume(player, held, count);

                if (clickedBe.hasConnectionCapacity())
                    WireAttachment.set(player, level, pos, wireItem, wireInfo);
                else WireAttachment.clear(player);
                message(player, "message.gtm_powerlines.wire.connected");
                return true;
            }
            case SAME_BLOCK -> message(player, "message.gtm_powerlines.wire.same_block_selected");
            case ALREADY_CONNECTED -> message(player, "message.gtm_powerlines.wire.already_connected");
            case TOO_MANY_CONNECTIONS -> message(player, "message.gtm_powerlines.wire.too_many_connections");
            case INCOMPATIBLE_TIER -> message(player, "message.gtm_powerlines.wire.incompatible_tier",
                    GTValues.VNF[connectResult.value()], GTValues.VNF[wireInfo.tier()]);
        }
        return false;
    }

    private static @Nullable InteractionHand handHolding(Player player, Item wire) {
        if (player.getMainHandItem().is(wire)) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(wire)) return InteractionHand.OFF_HAND;
        return null;
    }

    @SubscribeEvent
    public static void onRightClickAir(PlayerInteractEvent.RightClickItem event) {
        var level = event.getLevel();
        if (level.isClientSide()) return;

        var player = event.getEntity();
        var attach = WireAttachment.get(player);
        if (attach == null || !event.getItemStack().is(attach.wire())) return;

        var time = level.getGameTime();
        var lastTime = PLAYER_TICKS.put(player.getUUID(), time);
        if (lastTime != null && lastTime == time) return;

        WireAttachment.clear(player);
        message(player, "message.gtm_powerlines.wire.cancelled");
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getSlot() != EquipmentSlot.MAINHAND && event.getSlot() != EquipmentSlot.OFFHAND) return;

        var attach = WireAttachment.get(player);
        if (attach == null || handHolding(player, attach.wire()) != null) return;

        WireAttachment.clear(player);
        player.displayClientMessage(Component.translatable("message.gtm_powerlines.wire.cancelled"), true);
    }

    private static void message(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }

    private static void finish(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
