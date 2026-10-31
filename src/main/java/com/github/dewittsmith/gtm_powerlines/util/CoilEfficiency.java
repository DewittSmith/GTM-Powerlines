package com.github.dewittsmith.gtm_powerlines.util;

import com.gregtechceu.gtceu.api.block.ICoilType;
import net.minecraft.util.Mth;

public class CoilEfficiency {
    public static float of(ICoilType coilType) {
        return Mth.clamp(0.5f * (1 + Mth.sqrt(coilType.getTier() / 7f)), 0f, 1f);
    }
}
