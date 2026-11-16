package com.github.dewittsmith.gtm_powerlines.item;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.network.MultimeterPathPacket;
import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.wire.MultimeterHighlight;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import static com.gregtechceu.gtceu.utils.FormattingUtil.DECIMAL_FORMAT_1F;

public class MultimeterItem extends Item {

    private static final String TAG_POS = "SelectedInsulator";
    private static final String TAG_DIM = "SelectedDimension";

    public MultimeterItem(Properties properties) {
        super(properties);
    }

    private static @Nullable BlockPos getSelection(ItemStack stack, Level level) {
        var posTag = stack.getTagElement(TAG_POS);
        var root = stack.getTag();
        if (posTag == null || root == null) return null;
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(root.getString(TAG_DIM)));
        return dimension.equals(level.dimension()) ? NbtUtils.readBlockPos(posTag) : null;
    }

    private static void setSelection(ItemStack stack, Level level, BlockPos pos) {
        stack.getOrCreateTag().put(TAG_POS, NbtUtils.writeBlockPos(pos));
        stack.getOrCreateTag().putString(TAG_DIM, level.dimension().location().toString());
    }

    private static void clearSelection(ItemStack stack) {
        stack.removeTagKey(TAG_POS);
        stack.removeTagKey(TAG_DIM);
    }

    private static void report(Player player, ServerLevel level, BlockPos from, BlockPos to) {
        var network = PowerlineNetwork.get(level);
        var route = network.findRoute(from, to);
        if (route == null) {
            player.sendSystemMessage(Component.translatable("message.gtm_powerlines.multimeter.not_connected")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            var insulators = new ArrayList<BlockPos>(route.path().length + 1);
            var cursor = from;
            insulators.add(cursor);
            for (var edge : route.path()) {
                cursor = edge.other(cursor);
                insulators.add(cursor);
            }
            Net.sendToPlayer(serverPlayer, new MultimeterPathPacket(insulators));
        }

        var voltage = 0L;
        var amperage = 0.0;
        for (var edge : route.path()) {
            voltage = Math.max(voltage, network.getCurrentVoltage(edge));
            amperage = Math.max(amperage, network.getAverageAmperage(edge));
        }

        var voltageLine = Component.translatable("gtceu.top.cable_voltage");
        if (voltage != 0) voltageLine.append(GTValues.VNF[GTUtil.getTierByVoltage(voltage)]).append(" / ");
        voltageLine.append(GTValues.VNF[GTUtil.getTierByVoltage(route.minVoltage())]);
        player.sendSystemMessage(voltageLine);

        var amperageLine = Component.translatable("gtceu.top.cable_amperage");
        if (amperage != 0) amperageLine.append(DECIMAL_FORMAT_1F.format(amperage) + "A / ");
        amperageLine.append(DECIMAL_FORMAT_1F.format((double) route.minAmperage()) + "A");
        player.sendSystemMessage(amperageLine);

        player.sendSystemMessage(Component.translatable("gtm_powerlines.top.wire_length")
                .append(DECIMAL_FORMAT_1F.format(route.length()) + "m"));
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var player = context.getPlayer();
        var stack = context.getItemInHand();
        if (player == null || !(level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            MultimeterHighlight.clear();
            return InteractionResult.SUCCESS;
        }

        var selected = getSelection(stack, level);
        if (selected == null || selected.equals(pos) || player.isShiftKeyDown()) {
            setSelection(stack, level, pos);
            player.displayClientMessage(Component.translatable("message.gtm_powerlines.multimeter.selected",
                    formatPos(pos)), true);
            return InteractionResult.SUCCESS;
        }

        clearSelection(stack);
        report(player, serverLevel, selected, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide) MultimeterHighlight.clear();
        if (player.isShiftKeyDown() && stack.getTagElement(TAG_POS) != null) {
            if (!level.isClientSide) {
                clearSelection(stack);
                player.displayClientMessage(Component.translatable("message.gtm_powerlines.multimeter.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gtm_powerlines.multimeter.usage")
                .withStyle(ChatFormatting.GRAY));
        var tag = stack.getTagElement(TAG_POS);
        if (tag != null) {
            tooltip.add(Component.translatable("tooltip.gtm_powerlines.multimeter.selected",
                    formatPos(NbtUtils.readBlockPos(tag))).withStyle(ChatFormatting.YELLOW));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getTagElement(TAG_POS) != null;
    }
}
