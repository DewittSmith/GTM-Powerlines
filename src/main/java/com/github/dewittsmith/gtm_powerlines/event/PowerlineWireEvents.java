package com.github.dewittsmith.gtm_powerlines.event;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.wire.WireAttachment;
import com.github.dewittsmith.gtm_powerlines.wire.WireColor;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        if (!isPowerlineWire(held)) return;

        var wireItem = held.getItem();
        var wireColor = WireColor.wireColor(wireItem);

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
            if (clickedBe.getController() == null) {
                message(player, "message.gtm_powerlines.wire.insulator_not_in_structure");
                finish(event);
                return;
            }

            if (!clickedBe.hasConnectionCapacity()) {
                message(player, "message.gtm_powerlines.wire.too_many_connections");
                finish(event);
                return;
            }

            WireAttachment.set(player, level, pos, wireItem, wireColor);
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

        if (!(level.getBlockEntity(attach.pos()) instanceof PowerlineInsulatorBlockEntity fromBe) || fromBe.getController() == null) {
            WireAttachment.clear(player);
            message(player, "message.gtm_powerlines.wire.start_insulator_gone");
            finish(event);
            return;
        }

        BlockPos fromController = fromBe.getController();
        BlockPos toController = clickedBe.getController();
        if (toController == null) {
            message(player, "message.gtm_powerlines.wire.insulator_not_in_structure");
            finish(event);
            return;
        }

        if (fromController.equals(toController)) {
            WireAttachment.set(player, level, pos, wireItem, wireColor);
            message(player, "message.gtm_powerlines.wire.attached_click_another");
            finish(event);
            return;
        }

        var distance = Math.sqrt(attach.pos().distSqr(pos));
        if (distance < GTMPowerlinesConfig.INSTANCE.minWireRange) {
            message(player, "message.gtm_powerlines.wire.too_short", Mth.floor(distance), Mth.ceil(GTMPowerlinesConfig.INSTANCE.minWireRange));
            finish(event);
            return;
        }

        var maxDistance = Math.max(clickedBe.maxConnectionRange(), fromBe.maxConnectionRange());
        if (distance > maxDistance) {
            message(player, "message.gtm_powerlines.wire.too_long", Mth.ceil(distance), Mth.floor(maxDistance));
            finish(event);
            return;
        }

        ResourceLocation wire = ForgeRegistries.ITEMS.getKey(held.getItem());
        switch (fromBe.connect(pos, wire)) {
            case SUCCESS -> {
                clickedBe.connect(attach.pos(), wire);

                if (!player.getAbilities().instabuild) held.shrink(1);
                WireAttachment.set(player, level, pos, wireItem, wireColor);
                message(player, "message.gtm_powerlines.wire.connected");
                finish(event);
            }
            case SAME_BLOCK -> message(player, "message.gtm_powerlines.wire.same_block_selected");
            case ALREADY_CONNECTED -> message(player, "message.gtm_powerlines.wire.already_connected");
            case TOO_MANY_CONNECTIONS -> message(player, "message.gtm_powerlines.wire.too_many_connections");
        }
    }

    @SubscribeEvent
    public static void onRightClickAir(PlayerInteractEvent.RightClickItem event) {
        var level = event.getLevel();
        if (level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!isPowerlineWire(event.getItemStack())) return;

        var attach = WireAttachment.get(event.getEntity());
        if (attach == null) return;

        Player player = event.getEntity();
        var time = level.getGameTime();
        var lastTime = PLAYER_TICKS.put(player.getUUID(), time);
        if (lastTime == time) return;

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

    private static boolean isPowerlineWire(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return ChemicalHelper.getPrefix(stack.getItem()) == TagPrefix.wireFine;
    }

    private static void message(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }

    private static void finish(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}

