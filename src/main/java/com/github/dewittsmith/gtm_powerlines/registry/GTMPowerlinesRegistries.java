package com.github.dewittsmith.gtm_powerlines.registry;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;

import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.machines.PowerlineTowerMachine;
import com.github.dewittsmith.gtm_powerlines.machines.PowerlineTransformerMachine;
import com.github.dewittsmith.gtm_powerlines.util.CoilEfficiency;
import com.github.dewittsmith.gtm_powerlines.util.PowerlinePredicates;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;

import java.util.ArrayList;
import java.util.Comparator;

import static com.github.dewittsmith.gtm_powerlines.GTMPowerlines.POWERLINE_CREATIVE_TAB;
import static com.github.dewittsmith.gtm_powerlines.GTMPowerlines.POWERLINE_REGISTRATE;
import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.*;

public class GTMPowerlinesRegistries {

    static {
        POWERLINE_REGISTRATE.creativeModeTab(() -> POWERLINE_CREATIVE_TAB);
    }

    public static final BlockEntry<PowerlineInsulatorBlock> POWERLINE_INSULATOR = POWERLINE_REGISTRATE
            .block("powerline_insulator", PowerlineInsulatorBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.noOcclusion().strength(1.5f, 6f).sound(SoundType.GLASS))
            .blockstate((ctx, prov) -> {})
            .simpleItem()
            .register();

    public static final BlockEntityEntry<PowerlineInsulatorBlockEntity> POWERLINE_INSULATOR_BLOCK_ENTITY = POWERLINE_REGISTRATE
            .blockEntity("powerline_insulator", PowerlineInsulatorBlockEntity::new)
            .validBlock(POWERLINE_INSULATOR)
            .register();

    public static final MachineDefinition POWERLINE_TOWER = POWERLINE_REGISTRATE
            .multiblock("powerline_tower", PowerlineTowerMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .noRecipeModifier()
            .appearanceBlock(CASING_STEEL_SOLID)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("S", "F", "I")
                    .where('S', controller(blocks(definition.get())))
                    .where('F', PowerlinePredicates.towerBlocks())
                    .where('I',
                            PowerlinePredicates.track("insulators", POWERLINE_INSULATOR.getDefaultState())
                                    .setMinGlobalLimited(1))
                    .build())
            .shapeInfos(definition -> {
                var shapeInfo = new ArrayList<MultiblockShapeInfo>();
                shapeInfo.add(MultiblockShapeInfo.builder()
                        .aisle("S", "F", "I")
                        .where('S', definition, Direction.NORTH)
                        .where('F', Blocks.SPRUCE_FENCE)
                        .where('I', POWERLINE_INSULATOR)
                        .build());
                shapeInfo.add(MultiblockShapeInfo.builder()
                        .aisle("S", "D", "D", "F", "F", "F", "I")
                        .where('S', definition, Direction.NORTH)
                        .where('F', Blocks.SPRUCE_FENCE)
                        .where('I', POWERLINE_INSULATOR)
                        .where('D', Blocks.POLISHED_DEEPSLATE_WALL)
                        .build());
                shapeInfo.add(MultiblockShapeInfo.builder()
                        .aisle("S#", "D#", "D#", "F#", "F#", "FI", "LL")
                        .where('S', definition, Direction.NORTH)
                        .where('F', Blocks.SPRUCE_FENCE)
                        .where('I',
                                POWERLINE_INSULATOR.getDefaultState().setValue(PowerlineInsulatorBlock.DIRECTION,
                                        Direction.DOWN))
                        .where('D', Blocks.POLISHED_DEEPSLATE_WALL)
                        .where('L', Blocks.SPRUCE_SLAB)
                        .where('#', Blocks.AIR)
                        .build());
                shapeInfo.add(MultiblockShapeInfo.builder()
                        .aisle("#S#", "#D#", "#D#", "#F#", "#F#", "FFF", "IFI")
                        .where('S', definition, Direction.NORTH)
                        .where('F', Blocks.SPRUCE_FENCE)
                        .where('I', POWERLINE_INSULATOR)
                        .where('D', Blocks.POLISHED_DEEPSLATE_WALL)
                        .where('#', Blocks.AIR)
                        .build());
                shapeInfo.add(MultiblockShapeInfo.builder()
                        .aisle("##C#####C##",
                                "##D#####D##",
                                "##D#####D##",
                                "##D#####D##",
                                "##F#####F##",
                                "##FGF#FGF##",
                                "##F#FOF#F##",
                                "##D##F##D##",
                                "##F##F##F##",
                                "##FGFFFGF##",
                                "##F#####F##",
                                "##F#####F##",
                                "I#F##I##F#I",
                                "F#F##F##F#F",
                                "SSPSSSSSPSS",
                                "##F#####F##",
                                "##J#####J##")
                        .where('O', definition, Direction.NORTH)
                        .where('F', Blocks.SPRUCE_FENCE)
                        .where('I',
                                POWERLINE_INSULATOR.getDefaultState().setValue(PowerlineInsulatorBlock.DIRECTION,
                                        Direction.DOWN))
                        .where('D', Blocks.POLISHED_DEEPSLATE_WALL)
                        .where('#', Blocks.AIR)
                        .where('C', CASING_STEEL_SOLID)
                        .where('G', Blocks.SPRUCE_FENCE_GATE)
                        .where('S', Blocks.SPRUCE_SLAB)
                        .where('P', Blocks.SPRUCE_PLANKS)
                        .where('J', POWERLINE_INSULATOR)
                        .build());
                return shapeInfo;
            })
            .workableCasingModel(
                    GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                    GTMPowerlines.id("block/multiblock/powerline_tower"))
            .register();

    public static final MultiblockMachineDefinition POWERLINE_TRANSFORMER = POWERLINE_REGISTRATE
            .multiblock("powerline_transformer", PowerlineTransformerMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .noRecipeModifier()
            .appearanceBlock(CASING_STEEL_SOLID)
            .pattern(definition -> FactoryBlockPattern.start()
                    .aisle("RCXCR", "RCXCR", "RCXCR", "I###I")
                    .aisle("#CFC#", "#CFC#", "#CFC#", "#####")
                    .aisle("RCPCR", "RCPCR", "RCPCR", "I###I")
                    .aisle("#CFC#", "#CFC#", "#CFC#", "#####")
                    .aisle("RCXCR", "RCSCR", "RCXCR", "I###I")
                    .where('S', controller(blocks(definition.get())))
                    .where('R', blocks(FIREBOX_STEEL.get()))
                    .where('C', heatingCoils())
                    .where('X', blocks(CASING_STEEL_SOLID.get())
                            .or(PowerlinePredicates.powerlineHatch().setMinGlobalLimited(1))
                            .or(autoAbilities(true, false, false)))
                    .where('F', frames(GTMaterials.Steel))
                    .where('P', blocks(CASING_STEEL_PIPE.get()))
                    .where('I',
                            PowerlinePredicates
                                    .track("insulators", POWERLINE_INSULATOR.getDefaultState(),
                                            PowerlineInsulatorBlock.DIRECTION)
                                    .setExactLimit(6)
                                    .or(any()))
                    .where('#', air())
                    .build())
            .shapeInfos(definition -> {
                var shapeInfo = new ArrayList<MultiblockShapeInfo>();
                var builder = MultiblockShapeInfo.builder()
                        .aisle("RCTCR", "RCSCR", "RCMCR", "I###I")
                        .aisle("#CFC#", "#CFC#", "#CFC#", "#####")
                        .aisle("RCPCR", "RCPCR", "RCPCR", "I###I")
                        .aisle("#CFC#", "#CFC#", "#CFC#", "#####")
                        .aisle("RCXCR", "RCXCR", "RCXCR", "I###I")
                        .where('S', definition, Direction.NORTH)
                        .where('R', FIREBOX_STEEL)
                        .where('X', CASING_STEEL_SOLID)
                        .where('F', ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Steel))
                        .where('P', CASING_STEEL_PIPE)
                        .where('T', GTMachines.ENERGY_INPUT_HATCH[GTValues.LV], Direction.NORTH)
                        .where('M', GTMachines.MAINTENANCE_HATCH, Direction.NORTH)
                        .where('I', POWERLINE_INSULATOR)
                        .where('#', Blocks.AIR);
                GTCEuAPI.HEATING_COILS.entrySet().stream()
                        .sorted(Comparator.comparingInt(entry -> entry.getKey().getTier()))
                        .forEach(
                                coil -> shapeInfo.add(builder.shallowCopy().where('C', coil.getValue().get()).build()));
                return shapeInfo;
            })
            .workableCasingModel(
                    GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                    GTMPowerlines.id("block/multiblock/powerline_transformer"))
            .additionalDisplay((controller, components) -> {
                if (!(controller instanceof PowerlineTransformerMachine transformerMachine) ||
                        !transformerMachine.isFormed()) {
                    return;
                }

                components.add(Component.translatable("multiblock.gtm_powerlines.powerline_transformer.powerline_tier",
                        GTValues.VNF[transformerMachine.getTier()]));
                components.add(
                        Component.translatable("multiblock.gtm_powerlines.powerline_transformer.powerline_efficiency",
                                Mth.floor(100 * CoilEfficiency.of(transformerMachine.getCoilType()))));

                components.add(Component.translatable("multiblock.gtm_powerlines.powerline_transformer.mode")
                        .append(Component.translatable(transformerMachine.getIO().tooltip)));

                if (transformerMachine.isInput()) {
                    components
                            .add(Component.translatable("multiblock.gtm_powerlines.powerline_transformer.energy_output",
                                    transformerMachine.getEnergyOutputPerSec() / 20));
                    components.add(
                            Component.translatable("multiblock.gtm_powerlines.powerline_transformer.network_output",
                                    transformerMachine.getNetworkOutputPerSec() / 20));
                    components.add(Component.translatable("multiblock.gtm_powerlines.powerline_transformer.energy_loss",
                            (transformerMachine.getEnergyOutputPerSec() - transformerMachine.getNetworkOutputPerSec()) /
                                    20));
                } else {
                    components
                            .add(Component.translatable("multiblock.gtm_powerlines.powerline_transformer.energy_input",
                                    transformerMachine.getEnergyInput()));
                }

            })
            .register();

    public static void init() {}
}
