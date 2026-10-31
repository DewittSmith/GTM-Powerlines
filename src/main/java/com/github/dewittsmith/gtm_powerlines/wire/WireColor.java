package com.github.dewittsmith.gtm_powerlines.wire;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

public final class WireColor {
    private static final int DEFAULT_COLOR = 0xFF000000;

    public static int wireColor(ItemLike item) {
        if (item == null || item == Items.AIR) return DEFAULT_COLOR;
        var stack = ChemicalHelper.getMaterialEntry(item).material();
        return 0xFF000000 | stack.getMaterialRGB();
    }

    public static int wireColor(ResourceLocation wireId) {
        var item = ForgeRegistries.ITEMS.getValue(wireId);
        return wireColor(item);
    }
}