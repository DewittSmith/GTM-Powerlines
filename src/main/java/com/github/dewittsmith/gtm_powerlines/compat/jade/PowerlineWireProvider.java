package com.github.dewittsmith.gtm_powerlines.compat.jade;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.IPaintable;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWireStats;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;
import com.github.dewittsmith.gtm_powerlines.wire.WireTargeting;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;

import javax.annotation.Nullable;

import static com.gregtechceu.gtceu.utils.FormattingUtil.DECIMAL_FORMAT_1F;

/**
 * Jade tooltip for suspended cables. Cables are neither blocks nor entities, so a ray-trace callback swaps the
 * target for a fake block accessor (shown as the wire item, without a block entity) whenever a cable is in front of
 * everything else. Live readings come from {@link ClientWireStats}.
 * <p>
 * Components follow GTCEu: voltage and amperage exactly as {@code CableBlockProvider} adds them, then the cable
 * length, then the paint colour as {@code StainedColorProvider} adds it.
 */
public class PowerlineWireProvider implements IBlockComponentProvider {

    private @Nullable IWailaClientRegistration registration;
    private @Nullable PowerlineGraph.Edge target;

    public void setClientRegistration(IWailaClientRegistration registration) {
        this.registration = registration;
    }

    public @Nullable Accessor<?> onRayTrace(HitResult hitResult, @Nullable Accessor<?> accessor,
                                            @Nullable Accessor<?> originalAccessor) {
        target = null;
        if (registration == null || hitResult instanceof EntityHitResult) return accessor;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null) return accessor;

        var hit = WireTargeting.pick(ClientWires.graph(), player, mc.getFrameTime());
        if (hit == null) return accessor;

        var item = ForgeRegistries.ITEMS.getValue(hit.edge().wire());
        if (item == null) return accessor;

        target = hit.edge();
        var blockHit = new BlockHitResult(hit.point(), player.getDirection().getOpposite(),
                BlockPos.containing(hit.point()), false);
        return registration.blockAccessor()
                .level(mc.level)
                .player(player)
                .blockState(GTMPowerlinesRegistries.POWERLINE_INSULATOR.getDefaultState())
                .hit(blockHit)
                .fakeBlock(new ItemStack(item))
                // own empty data: otherwise Jade hands over the last real target's data (e.g. a painted insulator)
                .serverData(new CompoundTag())
                .serverConnected(false)
                .showDetails(registration.isShowDetailsPressed())
                .build();
    }

    @Override
    public void appendTooltip(ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        var edge = target;
        if (edge == null || !blockAccessor.isFakeBlock() || edge.stats() == null) return;

        var props = edge.stats().props();
        var live = ClientWireStats.get(edge);
        long voltage = live == null ? 0 : live.voltage();
        double amperage = live == null ? 0 : live.amperage();

        // voltage & amperage: as GTCEu's CableBlockProvider
        iTooltip.add(Component.translatable("gtceu.top.cable_voltage"));
        if (voltage != 0) {
            iTooltip.append(Component.literal(GTValues.VNF[GTUtil.getTierByVoltage(voltage)]));
            iTooltip.append(Component.literal(" / "));
        }
        iTooltip.append(Component.literal(GTValues.VNF[GTUtil.getTierByVoltage(props.getVoltage())]));

        iTooltip.add(Component.translatable("gtceu.top.cable_amperage"));
        if (amperage != 0) {
            iTooltip.append(Component.literal(DECIMAL_FORMAT_1F.format(amperage) + "A / "));
        }
        iTooltip.append(Component.literal(DECIMAL_FORMAT_1F.format((double) props.getAmperage()) + "A"));

        // length: same shape as the lines above
        iTooltip.add(Component.translatable("gtm_powerlines.top.wire_length"));
        iTooltip.append(Component.literal(DECIMAL_FORMAT_1F.format(edge.length()) + "m"));

        // colour: as GTCEu's StainedColorProvider
        int paintingColor = edge.paint();
        if (paintingColor != IPaintable.UNPAINTED_COLOR) {
            iTooltip.add(Component.translatable("gtceu.top.stained", String.format("#%06X", paintingColor))
                    .withStyle(style -> style.withColor(paintingColor)));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return GTMPowerlines.id("wire_info");
    }
}
