package com.github.dewittsmith.gtm_powerlines.mixin;

import com.github.dewittsmith.gtm_powerlines.util.CoilEfficiency;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.utils.GTUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;

@Mixin(CoilBlock.class)
public abstract class CoilBlockMixin {

    @Inject(method = "appendHoverText", at = @At("RETURN"))
    private void gtmPowerlines$addEfficiency(ItemStack stack, @Nullable BlockGetter level,
                                             List<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        if (!GTUtil.isShiftDown()) return;

        CoilBlock self = (CoilBlock) (Object) this;
        tooltip.add(Component.translatable("tooltip.gtm_powerlines.coil_efficiency"));
        tooltip.add(Component.translatable("tooltip.gtm_powerlines.coil_efficiency_value", Mth.floor(100 * CoilEfficiency.of(self.coilType))));
    }
}