package com.github.dewittsmith.gtm_powerlines.wire;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.item.MaterialPipeBlockItem;
import com.gregtechceu.gtceu.common.block.CableBlock;
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
            if (!(item instanceof MaterialPipeBlockItem pipeItem)) return Optional.empty();
            var pipeBlock = pipeItem.getBlock();
            if (!(pipeBlock instanceof CableBlock cableBlock)) return Optional.empty();

            var wire = cableBlock.material.getProperty(PropertyKey.WIRE);
            if (wire == null) return Optional.empty();

            var color = cableBlock.pipeType.isCable ? 0xFF404040 : cableBlock.material.getMaterialARGB();
            return Optional.of(new WireStats(color, cableBlock.pipeType.modifyProperties(wire), cableBlock.pipeType));
        });
    }

    public int tier() {
        return GTUtil.getFloorTierByVoltage(props.getVoltage());
    }
}
