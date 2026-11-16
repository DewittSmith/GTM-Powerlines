package com.github.dewittsmith.gtm_powerlines.registry;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.item.MultimeterItem;
import com.github.dewittsmith.gtm_powerlines.machines.PowerlineTransformerMachine;
import com.github.dewittsmith.gtm_powerlines.util.CoilEfficiency;
import com.github.dewittsmith.gtm_powerlines.util.PowerlinePredicates;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

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

    public static final TagKey<Block> WIRE_PASSTHROUGH = BlockTags.create(GTMPowerlines.id("wire_passthrough"));

    public static final TagKey<Block> SURFACE = BlockTags.create(GTMPowerlines.id("surface"));
    public static final BlockEntry<PowerlineInsulatorBlock> INSULATOR = POWERLINE_REGISTRATE
            .block("insulator", PowerlineInsulatorBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .tag(CustomTags.MINEABLE_WITH_WRENCH, BlockTags.MINEABLE_WITH_PICKAXE)
            .properties(p -> p.noOcclusion().strength(1.5f, 6f).sound(SoundType.GLASS))
            .blockstate((ctx, prov) -> {})
            .simpleItem()
            .register();
    public static final BlockEntityEntry<PowerlineInsulatorBlockEntity> POWERLINE_INSULATOR_BLOCK_ENTITY = POWERLINE_REGISTRATE
            .blockEntity("insulator", PowerlineInsulatorBlockEntity::new)
            .validBlock(INSULATOR)
            .register();
    public static final ItemEntry<MultimeterItem> MULTIMETER = POWERLINE_REGISTRATE
            .item("multimeter", MultimeterItem::new)
            .properties(p -> p.stacksTo(1))
            .model(NonNullBiConsumer.noop())
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
                                    .track("insulators", INSULATOR.getDefaultState(),
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
                        .where('I', INSULATOR)
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
