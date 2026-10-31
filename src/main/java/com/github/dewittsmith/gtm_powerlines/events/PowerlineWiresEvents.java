package com.github.dewittsmith.gtm_powerlines.events;

import com.github.dewittsmith.gtm_powerlines.ConfigHolder;
import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.PowerlineWireRenderer;
import com.github.dewittsmith.gtm_powerlines.machines.PowerlineInsulatorBlockEntity;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
public class PowerlineWiresEvents {
    private static final Map<UUID, Long> PLAYER_TICKS = new HashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        if (level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (!isPowerlineWire(held)) return;

        var wireItem = held.getItem();
        var wireColor = PowerlineWireRenderer.wireColor(wireItem);

        BlockPos pos = event.getPos();
        var clickedBe = level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity be ? be : null;
        var attach = WireAttachment.get(player);

        var time = level.getGameTime();
        var lastTime = PLAYER_TICKS.put(player.getUUID(), time);
        if (lastTime != null && lastTime == time) return;

        // 3. RMB elsewhere with an attach position: cancel
        if (clickedBe == null) {
            if (attach != null) {
                WireAttachment.clear(player);
                message(player, "Wire cancelled");
                finish(event);
            }
            return;
        }

        // 1. No attach position yet: remember this insulator
        if (attach == null || !attach.dimension().equals(level.dimension())) {
            if (clickedBe.getController() == null) {
                message(player, "This insulator is not part of a structure");
                finish(event);
                return;
            }

            if (!clickedBe.hasConnectionCapacity()) {
                message(player, "Too many connections");
                finish(event);
                return;
            }

            WireAttachment.set(player, level, pos, wireItem, wireColor);
            message(player, "Wire attached, click another insulator");
            finish(event);
            return;
        }

        // 2. Attach position exists and an insulator was clicked
        if (attach.pos().equals(pos)) {                      // same insulator: unselect
            WireAttachment.clear(player);
            message(player, "Wire cancelled");
            finish(event);
            return;
        }

        if (!(level.getBlockEntity(attach.pos()) instanceof PowerlineInsulatorBlockEntity fromBe)
                || fromBe.getController() == null) {
            WireAttachment.clear(player);                    // the start insulator disappeared
            message(player, "Start insulator is gone");
            finish(event);
            return;
        }

        BlockPos fromController = fromBe.getController();
        BlockPos toController = clickedBe.getController();
        if (toController == null) {
            message(player, "This insulator is not part of a structure");
            finish(event);
            return;
        }

        if (fromController.equals(toController)) {           // same machine: move the attach position instead
            WireAttachment.set(player, level, pos, wireItem, wireColor);
            message(player, "Wire attached, click another insulator");
            finish(event);
            return;
        }

        // distance between the two insulators
        double distance = Math.sqrt(attach.pos().distSqr(pos));
        if (distance < ConfigHolder.INSTANCE.minWireRange) {
            message(player, String.format("Wire too short (%.1f / min %.1f)", distance, ConfigHolder.INSTANCE.minWireRange));
            finish(event);
            return;
        }
        if (distance > ConfigHolder.INSTANCE.maxWireRange) {
            message(player, String.format("Wire too long (%.1f / max %.1f)", distance, ConfigHolder.INSTANCE.maxWireRange));
            finish(event);
            return;
        }

        ResourceLocation wire = ForgeRegistries.ITEMS.getKey(held.getItem());
        switch (fromBe.connect(pos, wire)) {
            case SUCCESS -> {
                clickedBe.connect(attach.pos(), wire);

                if (!player.getAbilities().instabuild) held.shrink(1);
                WireAttachment.set(player, level, pos, wireItem, wireColor);              // chain: continue from the new insulator
                message(player, "Connected");
                finish(event);
            }
            case SAME_BLOCK -> message(player, "Same block selected");
            case ALREADY_CONNECTED -> message(player, "Already connected");
            case TOO_MANY_CONNECTIONS -> message(player, "Too many connections");
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
        message(event.getEntity(), "Wire cancelled");
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getSlot() != EquipmentSlot.MAINHAND) return;

        var attach = WireAttachment.get(player);
        if (attach == null) return;

        ItemStack now = event.getTo();
        if (attach.wire().equals(now.getItem())) return;       // same item, e.g. the stack count changed

        WireAttachment.clear(player);
        player.displayClientMessage(Component.literal("Wire cancelled"), true);
    }

    private static boolean isPowerlineWire(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return ChemicalHelper.getPrefix(stack.getItem()) == TagPrefix.wireFine;
    }

    private static void message(Player p, String text) {
        p.displayClientMessage(Component.literal(text), true);   // action bar above the hotbar
    }

    private static void finish(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}

