package com.github.dewittsmith.gtm_powerlines.renderer;

import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;

public enum WireLOD {
    TUBE,
    BILLBOARD,
    CULL;

    public static WireLOD getLOD(double distance) {

        WireLOD selected;
        if (distance < GTMPowerlinesConfig.INSTANCE.tubeWireLODDistance) selected = WireLOD.TUBE;
        else if (distance < GTMPowerlinesConfig.INSTANCE.flatWireLODDistance) selected = WireLOD.BILLBOARD;
        else selected = WireLOD.CULL;
        return selected == TUBE && GTMPowerlinesConfig.INSTANCE.useFlatWires ? WireLOD.BILLBOARD : selected;
    }
}
