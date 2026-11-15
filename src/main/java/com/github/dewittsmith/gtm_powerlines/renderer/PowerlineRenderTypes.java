package com.github.dewittsmith.gtm_powerlines.renderer;

import net.minecraft.client.renderer.RenderType;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.OptionalDouble;

/** Render types for highlights that are drawn on top of the world geometry (no depth test). */
public final class PowerlineRenderTypes extends RenderType {

    public static final RenderType LINES_ON_TOP = create("gtm_powerlines_lines_on_top",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
            onTopState());

    public static final RenderType LINE_STRIP_ON_TOP = create("gtm_powerlines_line_strip_on_top",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINE_STRIP, 256, false, false,
            onTopState());

    /**
     * Vanilla's NO_DEPTH_TEST only skips enabling the depth test, so it is ignored when the depth test is already on
     * (e.g. after particles are rendered). This shard actually turns it off and restores it afterwards.
     * Created here rather than in a static field so static initialization order can never leave it null.
     */
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

    private PowerlineRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                 boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
