package com.github.dewittsmith.gtm_powerlines.client.renderer;

import net.minecraft.client.renderer.RenderType;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.OptionalDouble;

public final class PowerlineRenderTypes extends RenderType {

    public static final RenderType LINES_ON_TOP = create("gtm_powerlines_lines_on_top",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
            onTopState());

    public static final RenderType LINE_STRIP_ON_TOP = create("gtm_powerlines_line_strip_on_top",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINE_STRIP, 256, false, false,
            onTopState());

    /**
     * Like Unity's Unlit/Color: flat vertex colour only. No texture, no lightmap, no diffuse shading.
     * Back faces are culled and depth is tested/written like regular geometry.
     */
    public static final RenderType UNLIT_COLOR = create("gtm_powerlines_unlit_color",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTextureState(NO_TEXTURE)
                    .setLightmapState(NO_LIGHTMAP)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(CULL)
                    .createCompositeState(false));

    private PowerlineRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                 boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    private static CompositeState onTopState() {
        var depthOff = new LayeringStateShard("gtm_powerlines_depth_off",
                RenderSystem::disableDepthTest, RenderSystem::enableDepthTest);
        return CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER)
                .setLineState(new LineStateShard(OptionalDouble.empty()))
                .setLayeringState(depthOff)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setOutputState(MAIN_TARGET)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST)
                .createCompositeState(false);
    }
}
