package com.github.dewittsmith.gtm_powerlines.wire;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.pipelike.cable.Insulation;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record WireStats(int color, WireProperties props, Insulation insulation) {

    private static final Map<Item, WireStats> CACHED_INFO = new HashMap<>();

    public static Optional<WireStats> of(ResourceLocation wireId) {
        var item = ForgeRegistries.ITEMS.getValue(wireId);
        return of(item);
    }

    public static Optional<WireStats> of(ItemLike itemLike) {
        if (itemLike == null) return Optional.empty();

        var result = CACHED_INFO.computeIfAbsent(itemLike.asItem(), item -> {
            if (ChemicalHelper.getPrefix(item) != TagPrefix.wireFine) return null;
            var stack = ChemicalHelper.getMaterialStack(item);
            var wire = stack.material().getProperty(PropertyKey.WIRE);
            if (wire == null) return null;
            wire = Insulation.WIRE_SINGLE.modifyProperties(wire);

            var color = 0xFF000000 | stack.material().getMaterialRGB();
            return new WireStats(color, wire, Insulation.WIRE_SINGLE);
        });

        if (result == null) return Optional.empty();
        return Optional.of(result);
    }

    public int tier() {
        return GTUtil.getFloorTierByVoltage(props.getVoltage());
    }
}
