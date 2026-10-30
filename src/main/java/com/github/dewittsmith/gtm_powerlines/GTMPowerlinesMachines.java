package com.github.dewittsmith.gtm_powerlines;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;

import java.util.ArrayList;
import java.util.Comparator;

import static com.github.dewittsmith.gtm_powerlines.GTMPowerlines.POWERLINE_CREATIVE_TAB;
import static com.github.dewittsmith.gtm_powerlines.GTMPowerlines.POWERLINE_REGISTRATE;
import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.*;

public class GTMPowerlinesMachines {
    static {
        POWERLINE_REGISTRATE.creativeModeTab(() -> POWERLINE_CREATIVE_TAB);
    }

    public static final BlockEntry<PowerlineInsulator> POWERLINE_INSULATOR = POWERLINE_REGISTRATE
            .block("powerline_insulator", PowerlineInsulator::new)
            .lang("Powerline Insulator")
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.noOcclusion().strength(1.5f, 6f).sound(SoundType.GLASS))
            .blockstate((ctx, prov) -> {
            })
            .simpleItem()
            .register();

    public static final MultiblockMachineDefinition POWERLINE_TRANSFORMER = POWERLINE_REGISTRATE
            .multiblock("powerline_transformer", PowerlineTransformer::new)
            .langValue("Powerline Transformer")
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .recipeModifier(RecipeModifier.NO_MODIFIER)
            .appearanceBlock(MACHINE_CASING_LV)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("RCXCR", "RCXCR", "RCXCR", "*****")
                    .aisle("#CFC#", "#CFC#", "#CFC#", "*I*I*")
                    .aisle("RCPCR", "RCPCR", "RCPCR", "*****")
                    .aisle("#CFC#", "#CFC#", "#CFC#", "*****")
                    .aisle("RCXCR", "RCSCR", "RCXCR", "*****")
                    .where('S', controller(blocks(definition.get())))
                    .where('R', blocks(FIREBOX_STEEL.get()))
                    .where('C', heatingCoils())
                    .where('X', blocks(MACHINE_CASING_LV.get())
                            .or(abilities(PartAbility.INPUT_ENERGY, PartAbility.OUTPUT_ENERGY).setExactLimit(1))
                            .or(ability(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where('F', frames(GTMaterials.Steel))
                    .where('P', blocks(CASING_STEEL_PIPE.get()))
                    .where('I', states(POWERLINE_INSULATOR.getDefaultState())
                            .or(any()))
                    .where('#', air())
                    .where('*', any())
                    .build())
            .shapeInfos(definition -> {
                var shapeInfo = new ArrayList<MultiblockShapeInfo>();
                var builder = MultiblockShapeInfo.builder()
                        .aisle("RCTCR", "RCSCR", "RCMCR", "#####")
                        .aisle("#CFC#", "#CFC#", "#CFC#", "#####")
                        .aisle("RCPCR", "RCPCR", "RCPCR", "#####")
                        .aisle("#CFC#", "#CFC#", "#CFC#", "#I#I#")
                        .aisle("RCXCR", "RCXCR", "RCXCR", "#####")
                        .where('S', definition, Direction.NORTH)
                        .where('R', FIREBOX_STEEL.get())
                        .where('X', MACHINE_CASING_LV.get())
                        .where('F', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Steel))
                        .where('P', CASING_STEEL_PIPE.get())
                        .where('T', GTMachines.ENERGY_INPUT_HATCH[GTValues.LV], Direction.NORTH)
                        .where('M', GTMachines.MAINTENANCE_HATCH, Direction.NORTH)
                        .where('I', POWERLINE_INSULATOR.getDefaultState())
                        .where('#', Blocks.AIR.defaultBlockState());
                GTCEuAPI.HEATING_COILS.entrySet().stream()
                        .sorted(Comparator.comparingInt(entry -> entry.getKey().getTier()))
                        .forEach(coil -> shapeInfo.add(builder.shallowCopy().where('C', coil.getValue().get()).build()));
                return shapeInfo;
            })
            .workableCasingModel(
                    GTCEu.id("block/casings/voltage/lv/side"),
                    GTCEu.id("block/multiblock/implosion_compressor"))
            .register();

    public static void init() {
    }
}
