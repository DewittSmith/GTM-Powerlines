package com.github.dewittsmith.gtm_powerlines.registry;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.item.MultimeterItem;
import com.github.dewittsmith.gtm_powerlines.machines.PowerlineTransformerMachine;
import com.github.dewittsmith.gtm_powerlines.util.PowerlinePredicates;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

import static com.github.dewittsmith.gtm_powerlines.GTMPowerlines.POWERLINE_CREATIVE_TAB;
import static com.github.dewittsmith.gtm_powerlines.GTMPowerlines.POWERLINE_REGISTRATE;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
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
            .pattern(definition -> MultiblockPatternBuilder
                    .start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
                    .slice("RCXCR", "RCXCR", "RCXCR", "I###I")
                    .slice("#CFC#", "#CFC#", "#CFC#", "#####")
                    .slice("RCPCR", "RCPCR", "RCPCR", "I###I")
                    .slice("#CFC#", "#CFC#", "#CFC#", "#####")
                    .slice("RCXCR", "RCSCR", "RCXCR", "I###I")
                    .where('S', controller(definition))
                    .where('R', blocks(FIREBOX_STEEL.get()))
                    .where('C', heatingCoils())
                    .where('X', blocks(CASING_STEEL_SOLID.get())
                            .and(PowerlinePredicates.powerlineHatch().setMinCount(1))
                            .and(autoAbilities(true, false, false)))
                    .where('F', frames(GTMaterials.Steel))
                    .where('P', blocks(CASING_STEEL_PIPE.get()))
                    .where('I',
                            PowerlinePredicates
                                    .matchingStates(INSULATOR.getDefaultState(),
                                            PowerlineInsulatorBlock.DIRECTION)
                                    .setExactLimit(6)
                                    .and(any()))
                    .where('#', air())
                    .build())
            .workableCasingModel(
                    GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                    GTMPowerlines.id("block/multiblock/powerline_transformer"))
            .register();

    public static void init() {}
}
