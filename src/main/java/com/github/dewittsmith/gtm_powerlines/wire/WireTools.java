package com.github.dewittsmith.gtm_powerlines.wire;

import com.gregtechceu.gtceu.api.blockentity.IPaintable;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.ColorSprayBehaviour;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

/**
 * Helpers for the GT tools that interact with suspended cables.
 */
public final class WireTools {

    public static boolean isCutter(ItemStack stack) {
        return stack.is(CustomTags.WIRE_CUTTERS);
    }

    /**
     * @return the GT painting colour a spray can applies ({@link IPaintable#UNPAINTED_COLOR} for solvent), or empty
     *         if the stack is not a spray can
     */
    public static OptionalInt sprayColor(ItemStack stack) {
        if (stack.isEmpty()) return OptionalInt.empty();
        if (stack.is(GTItems.SPRAY_SOLVENT.get())) return OptionalInt.of(IPaintable.UNPAINTED_COLOR);
        var colors = DyeColor.values();
        for (int i = 0; i < GTItems.SPRAY_CAN_DYES.length; i++) {
            var entry = GTItems.SPRAY_CAN_DYES[i];
            if (entry != null && stack.is(entry.get())) return OptionalInt.of(colors[i].getMapColor().col);
        }
        return OptionalInt.empty();
    }

    public static boolean isSprayCan(ItemStack stack) {
        return sprayColor(stack).isPresent();
    }

    /** Tools that target cables rather than blocks while the player looks at one. */
    public static boolean targetsWires(ItemStack stack) {
        return isCutter(stack) || isSprayCan(stack);
    }

    /** Uses one charge of a spray can, turning it into an empty can when it runs out. */
    public static void useSprayCharge(Player player, InteractionHand hand, ItemStack stack) {
        if (!(stack.getItem() instanceof ComponentItem item)) return;
        for (var component : item.getComponents()) {
            if (component instanceof ColorSprayBehaviour spray) {
                spray.useItemDurability(player, hand, stack, GTItems.SPRAY_EMPTY.asStack());
                return;
            }
        }
    }
}
