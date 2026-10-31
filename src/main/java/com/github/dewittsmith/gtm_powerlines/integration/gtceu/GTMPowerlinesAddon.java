package com.github.dewittsmith.gtm_powerlines.integration.gtceu;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;

import java.util.function.Consumer;

@SuppressWarnings("unused")
@GTAddon
public class GTMPowerlinesAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return GTMPowerlines.POWERLINE_REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return GTMPowerlines.MOD_ID;
    }

    @Override
    public void registerTagPrefixes() {
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
    }

    @Override
    public void registerElements() {
    }
}
