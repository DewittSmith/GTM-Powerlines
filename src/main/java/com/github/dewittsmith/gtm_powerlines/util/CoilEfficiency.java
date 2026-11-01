package com.github.dewittsmith.gtm_powerlines.util;

import com.gregtechceu.gtceu.api.block.ICoilType;
import net.minecraft.util.Mth;

public class CoilEfficiency {
    public static float of(ICoilType coilType) {
        final float MIN_EFFICIENCY = 0.4f;
        final float MAX_EFFICIENCY = 0.8f;
        return Mth.clampedLerp(MIN_EFFICIENCY, MAX_EFFICIENCY, Mth.sqrt(coilType.getTier() / 7f));
    }
}
