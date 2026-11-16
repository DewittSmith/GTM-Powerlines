package com.github.dewittsmith.gtm_powerlines;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;

import com.github.dewittsmith.gtm_powerlines.data.GTMPowerlinesRecipes;

import java.util.function.Consumer;

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
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        GTMPowerlinesRecipes.init(provider);
    }
}
