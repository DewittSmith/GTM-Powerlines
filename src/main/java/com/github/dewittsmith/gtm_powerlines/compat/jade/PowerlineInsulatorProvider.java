package com.github.dewittsmith.gtm_powerlines.compat.jade;

import net.minecraft.ChatFormatting;
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
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

import java.util.HashMap;
import java.util.Map;

public class PowerlineInsulatorProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof PowerlineInsulatorBlockEntity insulator)) return;

        data.putBoolean("InStructure", insulator.getController() != null);
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
        CompoundTag data = accessor.getServerData();
        if (!data.contains("Connections")) return;

        if (!data.getBoolean("InStructure")) {
            tooltip.add(Component.translatable("message.gtm_powerlines.wire.insulator_not_in_structure")
                    .withStyle(ChatFormatting.RED));
        }

        tooltip.add(Component.translatable("tooltip.gtm_powerlines.connections",
                data.getInt("Connections"), data.getInt("MaxConnections")));
        tooltip.add(Component.translatable("tooltip.gtm_powerlines.max_distance",
                Mth.floor(data.getFloat("MaxRange"))));

        var helper = IElementHelper.get();
        for (var raw : data.getList("Wires", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(t.getString("Wire")));
            if (item == null) continue;
            var stack = new ItemStack(item);

            tooltip.add(helper.item(stack, 0.5f));
            tooltip.append(Component.literal(" " + t.getInt("Count") + "x ").append(stack.getHoverName()));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return new ResourceLocation(GTMPowerlines.MOD_ID, "powerline_insulator_info");
    }
}
