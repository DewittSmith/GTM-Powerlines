package com.github.dewittsmith.gtm_powerlines.item;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.graph.TransferPath;
import com.github.dewittsmith.gtm_powerlines.machines.PowerlineTransformerMachine;
import com.github.dewittsmith.gtm_powerlines.network.MultimeterPathPacket;
import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.util.BlockUtils;
import com.github.dewittsmith.gtm_powerlines.util.CoilEfficiency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

/**
 * Use on one insulator, then on another: prints the stats of the cable route between them to chat.
 * Shift-use in the air clears the selection.
 */
public class MultimeterItem extends Item {

    private static final String TAG_POS = "SelectedInsulator";
    private static final String TAG_DIM = "SelectedDimension";

    public MultimeterItem(Properties properties) {
        super(properties);
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
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

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

    // region selection

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

    // endregion

    private static void report(Player player, ServerLevel level, BlockPos from, BlockPos to) {
        var network = PowerlineNetwork.get(level);
        player.sendSystemMessage(Component.translatable("message.gtm_powerlines.multimeter.header",
                formatPos(from), formatPos(to)).withStyle(ChatFormatting.GOLD));

        TransferPath route = network.findRoute(from, to);
        if (route == null) {
            player.sendSystemMessage(Component.translatable("message.gtm_powerlines.multimeter.not_connected")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        // highlight the route for the player
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

        // line type
        var dedicated = network.getRoutes(from).dedicated();
        line(player, dedicated ? "message.gtm_powerlines.multimeter.line_transformer" :
                "message.gtm_powerlines.multimeter.line_machines");

        // geometry
        int items = 0;
        var wires = new LinkedHashMap<ResourceLocation, Integer>();
        for (var edge : route.path()) {
            items += edge.wireCount();
            wires.merge(edge.wire(), edge.wireCount(), Integer::sum);
        }
        line(player, "message.gtm_powerlines.multimeter.length", route.path().length,
                fmt(route.length()), items);

        MutableComponent wireList = Component.empty();
        var first = true;
        for (var entry : wires.entrySet()) {
            var item = ForgeRegistries.ITEMS.getValue(entry.getKey());
            if (!first) wireList.append(", ");
            first = false;
            wireList.append(item == null ? Component.literal(entry.getKey().toString()) :
                    new ItemStack(item).getHoverName().copy());
            wireList.append(" x" + entry.getValue());
        }
        line(player, "message.gtm_powerlines.multimeter.wires", wireList);

        // ratings
        line(player, "message.gtm_powerlines.multimeter.limits", route.minVoltage(),
                GTValues.VNF[GTUtil.getFloorTierByVoltage(route.minVoltage())], route.minAmperage());

        // loss
        var lossScale = transformerLossScale(level, network, from, to);
        if (lossScale < 1f) {
            line(player, "message.gtm_powerlines.multimeter.loss_transformer", fmt(route.loss()),
                    Mth.floor((1f - lossScale) * 100), fmt(route.loss() * lossScale));
        } else {
            line(player, "message.gtm_powerlines.multimeter.loss", fmt(route.loss()));
        }

        // live readings
        double amps = 0, energy = 0;
        int hottest = PowerlineNetwork.DEFAULT_TEMP;
        for (var edge : route.path()) {
            amps = Math.max(amps, network.getAverageAmperage(edge));
            energy = Math.max(energy, network.getAverageEnergy(edge));
            hottest = Math.max(hottest, network.getTemperature(edge));
        }
        line(player, "message.gtm_powerlines.multimeter.flow", fmt(amps), Math.round(energy));
        line(player, "message.gtm_powerlines.multimeter.temperature", hottest, CableBlockEntity.getMeltTemp());
    }

    /** Loss multiplier between two transformer insulators of different transformers, otherwise 1. */
    private static float transformerLossScale(ServerLevel level, PowerlineNetwork network, BlockPos from, BlockPos to) {
        var a = network.getTransformerController(from);
        var b = network.getTransformerController(to);
        if (a == null || b == null || a.equals(b)) return 1f;
        var ta = BlockUtils.instanceOf(level, a, PowerlineTransformerMachine.class).orElse(null);
        var tb = BlockUtils.instanceOf(level, b, PowerlineTransformerMachine.class).orElse(null);
        if (ta == null || tb == null || !ta.isFormed() || !tb.isFormed()) return 1f;
        return 1f - (CoilEfficiency.of(ta.getCoilType()) + CoilEfficiency.of(tb.getCoilType())) / 2f;
    }

    private static void line(Player player, String key, Object... args) {
        player.sendSystemMessage(Component.literal(" ").append(Component.translatable(key, args))
                .withStyle(ChatFormatting.GRAY));
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
