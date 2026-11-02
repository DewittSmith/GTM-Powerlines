package com.github.dewittsmith.gtm_powerlines.config;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.renderer.WireLOD;
import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Config(id = GTMPowerlines.MOD_ID)
public class GTMPowerlinesConfig {
    public static GTMPowerlinesConfig INSTANCE;
    private static final Object LOCK = new Object();

    public static void init() {
        synchronized (LOCK) {
            if (INSTANCE == null) {
                INSTANCE = Configuration.registerConfig(GTMPowerlinesConfig.class, ConfigFormats.yaml()).getConfigInstance();
            }
        }
    }

    @Configurable
    @Configurable.Comment({"Base energy lossPerBlock per block multiplier."})
    @Configurable.DecimalRange(min = 0.0f, max = 1.0f)
    public float baseLossMult = 0.5f;

    @Configurable
    @Configurable.Comment({"Minimal energy lossPerBlock per block."})
    @Configurable.DecimalRange(min = 0.0f)
    public float minLoss = 0f;

    @Configurable
    @Configurable.Comment({"Minimal wire block distance."})
    @Configurable.DecimalRange(min = 0.0f, max = 256.0f)
    public float minWireRange = 4f;

    @Configurable
    @Configurable.Comment({"Maximal wire block distance."})
    @Configurable.DecimalRange(min = 0.0f, max = 256.0f)
    public float maxWireRange = 128f;

    @Configurable
    @Configurable.Comment({"Exponent applied to tower range."})
    @Configurable.DecimalRange(min = 0.0f, max = 8.0f)
    public float towerRangePower = 2f;

    @Configurable
    @Configurable.Comment({"Block tags that count as tower structure, e.g. \"minecraft:fences\"."})
    @Configurable.ValueUpdateCallback(method = "onTagsChanged")
    public String[] towerStructureTags = new String[]{"minecraft:fences", "minecraft:walls", "minecraft:fence_gates", "minecraft:planks", "minecraft:slabs"};

    @Configurable
    @Configurable.Comment({"Whether to use billboards to render wires."})
    public boolean useFlatWires = false;

    @Configurable
    @Configurable.Comment({"Tube LOD distance."})
    public float tubeWireLODDistance = 16;

    @Configurable
    @Configurable.Comment({"Billboard LOD distance."})
    public float billboardLODDistance = 128;

    private transient Set<TagKey<Block>> resolvedTags;

    public void onTagsChanged(String[] newValue) {
        resolvedTags = null;
    }

    public Set<TagKey<Block>> towerTags() {
        Set<TagKey<Block>> tags = resolvedTags;
        if (tags == null) {
            tags = Arrays.stream(towerStructureTags)
                    .map(ResourceLocation::tryParse)
                    .filter(Objects::nonNull)
                    .map(id -> TagKey.create(Registries.BLOCK, id))
                    .collect(Collectors.toUnmodifiableSet());
            resolvedTags = tags;
        }
        return tags;
    }
}