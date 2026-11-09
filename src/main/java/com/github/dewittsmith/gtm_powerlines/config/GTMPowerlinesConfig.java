package com.github.dewittsmith.gtm_powerlines.config;

import com.gregtechceu.gtceu.utils.TagExprFilter;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

import java.util.Arrays;

@Config(id = GTMPowerlines.MOD_ID)
public class GTMPowerlinesConfig {

    public static GTMPowerlinesConfig INSTANCE;
    private static final Object LOCK = new Object();

    public static void init() {
        synchronized (LOCK) {
            if (INSTANCE == null) {
                INSTANCE = Configuration.registerConfig(GTMPowerlinesConfig.class, ConfigFormats.yaml())
                        .getConfigInstance();
            }
        }
    }

    @Configurable
    public WireConfig wire = new WireConfig();

    @Configurable
    public InsulatorConfig insulator = new InsulatorConfig();

    @Configurable
    public RenderingConfig rendering = new RenderingConfig();

    public static class WireConfig {

        @Configurable
        @Configurable.DecimalRange(min = 0f, max = 1f)
        @Configurable.Synchronized
        @Configurable.Gui.NumberFormat("0%")
        public float lowestCoilEfficiency = 0.4f;

        @Configurable
        @Configurable.DecimalRange(min = 0f, max = 1f)
        @Configurable.Synchronized
        @Configurable.Gui.NumberFormat("0%")
        public float highestCoilEfficiency = 0.8f;

        @Configurable
        @Configurable.DecimalRange(min = 0f)
        @Configurable.Synchronized
        public float minWireRange = 4f;

        @Configurable
        @Configurable.DecimalRange(min = 0f)
        @Configurable.Synchronized
        public float maxWireRange = 64f;
    }

    public static class InsulatorConfig {

        @Configurable
        @Configurable.DecimalRange(min = 0f, max = 8f)
        @Configurable.Comment(value = { "Insulator range is calculated as:", "pow(insulator.elevation, rangePower)" },
                              localize = true)
        @Configurable.Synchronized
        public float rangePower = 1.618f;

        @Configurable
        @Configurable.Comment(value = { "Max connections for an insulator",
                "Anything less than 2 treated as \"unlimited\"" }, localize = true)
        @Configurable.Synchronized
        public int maxWireConnections = 4;
    }

    public static class RenderingConfig {

        @Configurable
        @Configurable.Comment(value = { "Whether to use billboards to render wires" }, localize = true)
        public boolean useFlatWires = false;

        @Configurable
        @Configurable.DecimalRange(min = 0f)
        public float tubeWireLODDistance = 16;

        @Configurable
        @Configurable.DecimalRange(min = 0f)
        public float flatWireLODDistance = 128;
    }

    public static TagExprFilter.TagExprParser.MatchExpr getTagExpr(String[] tags, String fallback) {
        var combined = String.join("|", Arrays.stream(tags).map(s -> "(" + s + ")").toList());
        var result = TagExprFilter.parseExpression(combined);
        if (result == null) result = TagExprFilter.parseExpression(fallback);
        return result;
    }
}
