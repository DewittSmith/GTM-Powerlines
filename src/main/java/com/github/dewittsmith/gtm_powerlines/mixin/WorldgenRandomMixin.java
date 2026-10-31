package com.github.dewittsmith.gtm_powerlines.mixin;

import net.minecraft.world.level.levelgen.WorldgenRandom;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = WorldgenRandom.class, remap = false)
public class WorldgenRandomMixin {}
