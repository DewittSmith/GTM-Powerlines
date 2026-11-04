package com.github.dewittsmith.gtm_powerlines.config;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.gregtechceu.gtceu.utils.TagExprFilter;
import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.validate.IValidationResult;
import dev.toma.configuration.config.value.IConfigValueReadable;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.stream.Collectors;

@Config(id = GTMPowerlines.MOD_ID)
public class GTMPowerlinesConfig {
    public static GTMPowerlinesConfig INSTANCE;
    private static final Object LOCK = new Object();

    public static void init() {
        synchronized (LOCK) {
            if (INSTANCE == null) {
                var holder = Configuration.registerConfig(GTMPowerlinesConfig.class, ConfigFormats.YAML);
                INSTANCE = holder.getConfigInstance();
                holder.getConfigValue("tower.towerStructureFilters", String[].class)
                        .ifPresent(c -> c.addValidator(INSTANCE::onTagsChanged));
            }
        }
    }

    @Configurable
    public WireConfig wire = new WireConfig();

    @Configurable
    public TowerConfig tower = new TowerConfig();

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
        public float minLoss = 0f;

        @Configurable
        @Configurable.DecimalRange(min = 0f)
        @Configurable.Synchronized
        public float lossMultiplier = 1f;

        @Configurable
        @Configurable.DecimalRange(min = 0f)
        @Configurable.Synchronized
        public float minWireRange = 4f;

        @Configurable
        @Configurable.DecimalRange(min = 0f)
        @Configurable.Synchronized
        public float maxWireRange = 128f;
    }

    public static class TowerConfig {
        @Configurable
        @Configurable.DecimalRange(min = 0f, max = 8f)
        @Configurable.Comment(value = {"Tower range is calculated as:", "pow(insulator.y - tower_base.y, towerRangePower)"}, localize = true)
        @Configurable.Synchronized
        public float towerRangePower = 1.7f;

        @Configurable
        @Configurable.Range(min = 0)
        @Configurable.Synchronized
        public int maxTowerBlocks = 64;

        @Configurable
        @Configurable.Comment(value = {
                "Block filters that count as tower structure, e.g. \"minecraft:fences|minecraft:walls\"",
                "Uses tag expressions from GTCEU (A&B, A|B, A^B, etc)"},
                localize = true)
        @Configurable.Synchronized
        public String[] towerStructureFilters = new String[]{
                "minecraft:fences|minecraft:walls",
                "minecraft:slabs|minecraft:planks",
                "minecraft:fence_gates"
        };
    }

    public static class RenderingConfig {
        @Configurable
        @Configurable.Comment(value = {"Whether to use billboards to render wires"}, localize = true)
        public boolean useFlatWires = false;

        @Configurable
        @Configurable.DecimalRange(min = 0f)
        public float tubeWireLODDistance = 16;

        @Configurable
        @Configurable.DecimalRange(min = 0f)
        public float flatWireLODDistance = 128;
    }

    private transient TagExprFilter.TagExprParser.MatchExpr towerTagExpression;
    private final transient Object2BooleanArrayMap<Block> towerTagCache = new Object2BooleanArrayMap<>();

    private IValidationResult onTagsChanged(String[] newValue, IConfigValueReadable<String[]> readable) {
        var combined = String.join("|", Arrays.stream(newValue).map(s -> "(" + s + ")").toList());
        towerTagExpression = TagExprFilter.parseExpression(combined);
        if (towerTagExpression == null) towerTagExpression = TagExprFilter.parseExpression("!*");

        towerTagCache.clear();
        return IValidationResult.success();
    }

    public boolean isTowerBlock(BlockState state) {
        if (towerTagExpression == null) onTagsChanged(tower.towerStructureFilters, null);
        var block = state.getBlock();
        if (towerTagCache.containsKey(block)) return towerTagCache.getBoolean(block);

        var tagSet = state.getTags()
                .map(TagKey::location)
                .map(ResourceLocation::toString)
                .collect(Collectors.toSet());

        var result = towerTagExpression.matches(tagSet);
        towerTagCache.put(block, result);
        return result;
    }
}