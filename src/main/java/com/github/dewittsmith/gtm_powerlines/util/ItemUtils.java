package com.github.dewittsmith.gtm_powerlines.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ItemUtils {

    public static boolean canConsume(Player player, ItemStack held, int count) {
        if (player.getAbilities().instabuild) return true;
        return countAvailable(player, held) >= count;
    }

    public static int countAvailable(Player player, ItemStack held) {
        if (player.getAbilities().instabuild) return Integer.MAX_VALUE;

        int total = held.getCount();
        for (var stack : player.getInventory().items) {
            if (stack == held) continue;
            if (ItemStack.isSameItem(stack, held)) total += stack.getCount();
        }
        return total;
    }

    public static void consume(Player player, ItemStack held, int count) {
        if (player.getAbilities().instabuild) return;

        int left = count;
        for (var stack : player.getInventory().items) {
            if (left <= 0) break;
            if (stack == held || stack.isEmpty() || !ItemStack.isSameItem(stack, held)) continue;

            int take = Math.min(stack.getCount(), left);
            stack.shrink(take);
            left -= take;
        }

        if (left > 0) held.shrink(Math.min(left, held.getCount()));
        player.getInventory().setChanged();
    }
}
