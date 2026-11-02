package com.github.dewittsmith.gtm_powerlines.wire;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.utils.GTUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

public final class WireInfo {
    private static final int DEFAULT_COLOR = 0xFF000000;

    public static int getColor(ItemLike item) {
        if (item == null || item == Items.AIR) return DEFAULT_COLOR;
        var stack = ChemicalHelper.getMaterialEntry(item).material();
        return 0xFF000000 | stack.getMaterialRGB();
    }

    public static int getColor(ResourceLocation wireId) {
        var item = ForgeRegistries.ITEMS.getValue(wireId);
        return getColor(item);
    }

    public static int getTier(ResourceLocation wireId) {
        var item = ForgeRegistries.ITEMS.getValue(wireId);
        if (item == null) return -1;
        var stack = ChemicalHelper.getMaterialStack(item);
        var wire = stack.material().getProperty(PropertyKey.WIRE);
        if (wire == null) return -1;
        return GTUtil.getFloorTierByVoltage(wire.getVoltage());
    }
}