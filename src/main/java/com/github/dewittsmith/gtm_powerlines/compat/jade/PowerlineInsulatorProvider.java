package com.github.dewittsmith.gtm_powerlines.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

import java.util.HashMap;
import java.util.Map;

public class PowerlineInsulatorProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        // fake accessors target a cable, see PowerlineWireProvider
        if (accessor.isFakeBlock() || !(accessor.getBlockEntity() instanceof PowerlineInsulatorBlockEntity insulator)) {
            return;
        }

        data.putInt("MaxConnections", insulator.maxConnections());
        data.putFloat("MaxRange", insulator.maxConnectionRange());

        Map<ResourceLocation, Integer> counts = new HashMap<>();
        var used = 0;
        for (var edge : insulator.getConnections()) {
            counts.merge(edge.wire(), edge.wireCount(), Integer::sum);
            used++;
        }
        data.putInt("Connections", used);

        var wires = new ListTag();
        counts.forEach((id, count) -> {
            CompoundTag t = new CompoundTag();
            t.putString("Wire", id.toString());
            t.putInt("Count", count);
            wires.add(t);
        });
        data.put("Wires", wires);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.isFakeBlock()) return;
        var data = accessor.getServerData();
        if (!data.contains("Connections")) return;

        var maxConnections = data.getInt("MaxConnections");
        if (maxConnections == Integer.MAX_VALUE) {
            tooltip.add(Component.translatable("tooltip.gtm_powerlines.connections_nomax", data.getInt("Connections")));
        } else {
            tooltip.add(Component.translatable("tooltip.gtm_powerlines.connections",
                    data.getInt("Connections"), data.getInt("MaxConnections")));
        }

        tooltip.add(Component.translatable("tooltip.gtm_powerlines.max_distance",
                Mth.floor(data.getFloat("MaxRange"))));

        var helper = IElementHelper.get();
        for (var raw : data.getList("Wires", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(t.getString("Wire")));
            if (item == null) continue;
            var stack = new ItemStack(item);

            tooltip.add(helper.item(stack, 0.5f));
            tooltip.append(Component.literal(" " + t.getInt("Count") + "x ").append(stack.getHoverName()));
        }
    }

    /** Before GTCEu's providers (all at BODY), so GT's paint colour line stays last, like on GT blocks. */
    @Override
    public int getDefaultPriority() {
        return TooltipPosition.BODY - 100;
    }

    @Override
    public ResourceLocation getUid() {
        return GTMPowerlines.id("powerline_insulator_info");
    }
}
