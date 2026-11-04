package com.github.dewittsmith.gtm_powerlines.wire;

import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.utils.GTUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

public record WireInfo(int color, int tier, float lossPerBlock) {
    public static Optional<WireInfo> of(ResourceLocation wireId) {
        var item = ForgeRegistries.ITEMS.getValue(wireId);
        return of(item);
    }

    public static Optional<WireInfo> of(ItemLike item) {
        if (item == null) return Optional.empty();
        var stack = ChemicalHelper.getMaterialStack(item);
        var wire = stack.material().getProperty(PropertyKey.WIRE);
        if (wire == null) return Optional.empty();

        var color = 0xFF000000 | stack.material().getMaterialRGB();
        var tier = GTUtil.getFloorTierByVoltage(wire.getVoltage());

        var loss = wire.getLossPerBlock() * GTMPowerlinesConfig.INSTANCE.wire.lossMultiplier;
        loss = Math.max(loss, GTMPowerlinesConfig.INSTANCE.wire.minLoss);

        return Optional.of(new WireInfo(color, tier, loss));
    }
}