package com.github.dewittsmith.gtm_powerlines.event;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.util.ItemUtils;
import com.github.dewittsmith.gtm_powerlines.wire.WireAttachment;
import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import com.github.dewittsmith.gtm_powerlines.wire.WireObstruction;
import com.github.dewittsmith.gtm_powerlines.wire.WireStats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID)
public class PowerlineWireEvents {

    private static final Map<UUID, Long> PLAYER_TICKS = new HashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        if (level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        var wireItem = held.getItem();
        var wireInfoOpt = WireStats.of(held.getItem());
        if (wireInfoOpt.isEmpty()) return;
        var wireInfo = wireInfoOpt.get();
        var wire = ForgeRegistries.ITEMS.getKey(wireItem);

        BlockPos pos = event.getPos();
        var clickedBe = level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity be ? be : null;
        var attach = WireAttachment.get(player);

        var time = level.getGameTime();
        var lastTime = PLAYER_TICKS.put(player.getUUID(), time);
        if (lastTime != null && lastTime == time) return;

        if (clickedBe == null) {
            if (attach != null) {
                WireAttachment.clear(player);
                message(player, "message.gtm_powerlines.wire.cancelled");
                finish(event);
            }
            return;
        }

        if (attach == null || !attach.dimension().equals(level.dimension())) {
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

        if (!(level.getBlockEntity(attach.pos()) instanceof PowerlineInsulatorBlockEntity fromBe)) {
            WireAttachment.clear(player);
            message(player, "message.gtm_powerlines.wire.start_insulator_gone");
            finish(event);
            return;
        }

        var fromController = fromBe.getController();
        var toController = clickedBe.getController();

        if (fromController != null && fromController.equals(toController)) {
            var tier = clickedBe.getWireTier();
            if (tier != -1 && tier != wireInfo.tier()) {
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

        var flatDistance = Math.sqrt(attach.pos().atY(0).distSqr(pos.atY(0)));
        var maxDistance = clickedBe.maxConnectionRange() + fromBe.maxConnectionRange();
        if (flatDistance > maxDistance) {
            message(player, "message.gtm_powerlines.wire.too_long", Mth.ceil(flatDistance), Mth.floor(maxDistance));
            finish(event);
            return;
        }

        if (WireObstruction.enabled()) {
            var fromDir = fromBe.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION);
            var toDir = clickedBe.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION);
            var blocked = WireObstruction.find(level,
                    WireGeometry.getAttachmentPoint(attach.pos(), fromDir, wireInfo),
                    WireGeometry.getAttachmentPoint(pos, toDir, wireInfo));
            if (!blocked.isEmpty()) {
                var first = blocked.get(0);
                message(player, "message.gtm_powerlines.wire.blocked", blocked.size(),
                        first.getX() + ", " + first.getY() + ", " + first.getZ());
                finish(event);
                return;
            }
        }

        var count = PowerlineGraph.Edge.wireCount(attach.pos(),
                fromBe.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION), pos,
                clickedBe.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION));
        var haveCount = ItemUtils.countAvailable(player, held);
        if (haveCount < count) {
            message(player, "message.gtm_powerlines.wire.not_enough", haveCount, count);
            finish(event);
            return;
        }

        var connectResult = fromBe.connect(pos, wire);
        switch (connectResult.status()) {
            case SUCCESS -> {
                ItemUtils.consume(player, held, count);

                if (clickedBe.hasConnectionCapacity())
                    WireAttachment.set(player, level, pos, wireItem, wireInfo);
                message(player, "message.gtm_powerlines.wire.connected");
                finish(event);
            }
            case SAME_BLOCK -> message(player, "message.gtm_powerlines.wire.same_block_selected");
            case ALREADY_CONNECTED -> message(player, "message.gtm_powerlines.wire.already_connected");
            case TOO_MANY_CONNECTIONS -> message(player, "message.gtm_powerlines.wire.too_many_connections");
            case INCOMPATIBLE_TIER -> message(player, "message.gtm_powerlines.wire.incompatible_tier",
                    GTValues.VNF[connectResult.value()], GTValues.VNF[wireInfo.tier()]);
        }
    }

    @SubscribeEvent
    public static void onRightClickAir(PlayerInteractEvent.RightClickItem event) {
        var level = event.getLevel();
        if (level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (WireStats.of(event.getItemStack().getItem()).isEmpty()) return;

        var attach = WireAttachment.get(event.getEntity());
        if (attach == null) return;

        Player player = event.getEntity();
        var time = level.getGameTime();
        var lastTime = PLAYER_TICKS.put(player.getUUID(), time);
        if (lastTime != null && lastTime == time) return;

        WireAttachment.clear(event.getEntity());
        message(event.getEntity(), "message.gtm_powerlines.wire.cancelled");
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getSlot() != EquipmentSlot.MAINHAND) return;

        var attach = WireAttachment.get(player);
        if (attach == null) return;

        ItemStack now = event.getTo();
        if (attach.wire().equals(now.getItem())) return;

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
