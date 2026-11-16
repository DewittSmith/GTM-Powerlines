package com.github.dewittsmith.gtm_powerlines.compat.oculus;

import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraftforge.fml.ModList;

public final class OculusCompat {

    private static final boolean LOADED = ModList.get().isLoaded("oculus");

    private OculusCompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isRenderingShadowPass() {
        return LOADED && Api.isRenderingShadowPass();
    }

    private static final class Api {

        static boolean isRenderingShadowPass() {
            return IrisApi.getInstance().isRenderingShadowPass();
        }
    }
}
