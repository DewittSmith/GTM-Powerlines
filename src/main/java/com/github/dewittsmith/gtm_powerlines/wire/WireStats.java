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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public record WireStats(int color, WireProperties props, Insulation insulation) {

    private static final Map<Item, Optional<WireStats>> CACHED_INFO = new ConcurrentHashMap<>();

    public static Optional<WireStats> of(ResourceLocation wireId) {
        var item = ForgeRegistries.ITEMS.getValue(wireId);
        return of(item);
    }

    public static Optional<WireStats> of(ItemLike itemLike) {
        if (itemLike == null) return Optional.empty();

        return CACHED_INFO.computeIfAbsent(itemLike.asItem(), item -> {
            if (ChemicalHelper.getPrefix(item) != TagPrefix.wireFine) return Optional.empty();
            var stack = ChemicalHelper.getMaterialStack(item);
            var wire = stack.material().getProperty(PropertyKey.WIRE);
            if (wire == null) return Optional.empty();

            var color = 0xFF000000 | stack.material().getMaterialRGB();
            return Optional.of(new WireStats(color, Insulation.WIRE_SINGLE.modifyProperties(wire),
                    Insulation.WIRE_SINGLE));
        });
    }

    public int tier() {
        return GTUtil.getFloorTierByVoltage(props.getVoltage());
    }
}
