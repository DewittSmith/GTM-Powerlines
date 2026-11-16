package com.github.dewittsmith.gtm_powerlines.data;

import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;

import net.minecraft.data.recipes.FinishedRecipe;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;

import java.util.function.Consumer;

import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.VA;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.*;
import static com.gregtechceu.gtceu.common.data.GTMaterials.*;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.ASSEMBLER_RECIPES;

public final class GTMPowerlinesRecipes {

    private GTMPowerlinesRecipes() {}

    public static void init(Consumer<FinishedRecipe> provider) {
        ASSEMBLER_RECIPES.recipeBuilder(GTMPowerlines.id("insulator"))
                .inputItems(rod, Steel)
                .inputItems(screw, Iron, 2)
                .inputItems(plate, Rubber)
                .inputItems(plate, Glass, 4)
                .outputItems(GTMPowerlinesRegistries.INSULATOR.asStack(2))
                .duration(100)
                .EUt(VA[LV])
                .save(provider);

        ASSEMBLER_RECIPES.recipeBuilder(GTMPowerlines.id("powerline_transformer"))
                .inputItems(GTBlocks.CASING_STEEL_SOLID.asStack())
                .inputItems(plateDouble, Steel, 2)
                .inputItems(GTMachines.TRANSFORMER[LV])
                .inputItems(GTMPowerlinesRegistries.INSULATOR.asStack(2))
                .inputItems(wireFine, Copper, 4)
                .inputItems(CustomTags.LV_CIRCUITS)
                .outputItems(GTMPowerlinesRegistries.POWERLINE_TRANSFORMER)
                .duration(200)
                .EUt(VA[LV])
                .save(provider);

        VanillaRecipeHelper.addShapedRecipe(provider, GTMPowerlines.id("multimeter_crafting"),
                GTMPowerlinesRegistries.MULTIMETER.asStack(),
                "WGW", "CPx", " d ",
                'W', new MaterialEntry(wireGtSingle, Copper),
                'G', new MaterialEntry(plate, Glass),
                'C', CustomTags.LV_CIRCUITS,
                'P', new MaterialEntry(plate, Iron));

        ASSEMBLER_RECIPES.recipeBuilder(GTMPowerlines.id("multimeter"))
                .inputItems(CustomTags.LV_CIRCUITS)
                .inputItems(wireGtSingle, Copper, 2)
                .inputItems(plate, Glass)
                .inputItems(plate, Iron)
                .outputItems(GTMPowerlinesRegistries.MULTIMETER.asStack())
                .duration(100)
                .EUt(VA[LV])
                .save(provider);
    }
}
