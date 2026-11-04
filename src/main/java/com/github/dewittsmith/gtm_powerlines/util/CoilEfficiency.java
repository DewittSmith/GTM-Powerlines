package com.github.dewittsmith.gtm_powerlines.util;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.common.block.CoilBlock;

import net.minecraft.util.Mth;

import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;

public class CoilEfficiency {

    private final static float MIN_TEMPERATURE = CoilBlock.CoilType.CUPRONICKEL.getCoilTemperature();
    private final static float MAX_TEMPERATURE = CoilBlock.CoilType.TRITANIUM.getCoilTemperature();

    public static float of(ICoilType coilType) {
        var t = (coilType.getCoilTemperature() - MIN_TEMPERATURE) / (MAX_TEMPERATURE - MIN_TEMPERATURE);

        final float MIN_EFFICIENCY = GTMPowerlinesConfig.INSTANCE.wire.lowestCoilEfficiency;
        final float MAX_EFFICIENCY = GTMPowerlinesConfig.INSTANCE.wire.highestCoilEfficiency;
        return Mth.clampedLerp(MIN_EFFICIENCY, MAX_EFFICIENCY, Mth.sqrt(t));
    }
}
