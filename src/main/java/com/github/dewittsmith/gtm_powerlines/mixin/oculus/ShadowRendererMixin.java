package com.github.dewittsmith.gtm_powerlines.mixin.oculus;

import net.minecraft.client.renderer.RenderBuffers;

import com.github.dewittsmith.gtm_powerlines.client.renderer.WireShadowRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public abstract class ShadowRendererMixin {

    @Shadow
    @Final
    private RenderBuffers buffers;

    @Shadow
    @Final
    private boolean shouldRenderEntities;

    @Unique
    private PoseStack gtmPowerlines$modelView;

    @ModifyVariable(method = "renderShadows", at = @At("STORE"), ordinal = 0, require = 0)
    private PoseStack gtmPowerlines$captureModelView(PoseStack modelView) {
        gtmPowerlines$modelView = modelView;
        return modelView;
    }

    @Inject(method = "renderShadows",
            at = @At(value = "INVOKE_STRING",
                     target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
                     args = "ldc=draw entities",
                     remap = true),
            require = 0)
    private void gtmPowerlines$renderWireShadows(CallbackInfo ci) {
        if (gtmPowerlines$modelView == null || !shouldRenderEntities) return;
        WireShadowRenderer.render(gtmPowerlines$modelView, buffers.bufferSource());
    }
}
