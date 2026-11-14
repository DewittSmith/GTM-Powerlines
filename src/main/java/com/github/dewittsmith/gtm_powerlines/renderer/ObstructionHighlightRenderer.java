package com.github.dewittsmith.gtm_powerlines.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;
import com.github.dewittsmith.gtm_powerlines.wire.ObstructionHighlight;
import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;

import java.util.ArrayList;

/**
 * Fading red outlines of whatever was in the way: blocks blocking a new wire, or wires blocking a new block.
 * Fades out like the multimeter path highlight.
 */
@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, value = Dist.CLIENT)
public final class ObstructionHighlightRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var highlight = ObstructionHighlight.get();
        var mc = Minecraft.getInstance();
        if (highlight == null || mc.level == null) return;

        double duration = GTMPowerlinesConfig.INSTANCE.rendering.obstructionHighlightSeconds * 1000.0;
        double elapsed = System.currentTimeMillis() - highlight.startMillis();
        if (duration <= 0 || elapsed >= duration) {
            ObstructionHighlight.clear();
            return;
        }
        float alpha = (float) (1 - elapsed / duration);

        Vec3 cam = event.getCamera().getPosition();
        var pose = event.getPoseStack().last();
        var buffers = mc.renderBuffers().bufferSource();

        // blocks: red outline of their collision box
        if (!highlight.blocks().isEmpty()) {
            var consumer = buffers.getBuffer(RenderType.lines());
            for (var pos : highlight.blocks()) {
                var shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos);
                var box = (shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds())
                        .move(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z).inflate(0.002);
                LevelRenderer.renderLineBox(event.getPoseStack(), consumer, box, 1f, 0f, 0f, alpha);
            }
            buffers.endBatch(RenderType.lines());
        }

        // wires: red line strip along the sagging cable
        var graph = ClientWires.graph();
        for (int i = 0; i + 1 < highlight.wires().size(); i += 2) {
            var edge = graph.getEdge(highlight.wires().get(i), highlight.wires().get(i + 1)).orElse(null);
            if (edge == null || edge.stats() == null) continue;

            var start = WireGeometry.getAttachmentPoint(edge.a(), edge.dirA(), edge.stats());
            var end = WireGeometry.getAttachmentPoint(edge.b(), edge.dirB(), edge.stats());
            double sag = WireGeometry.sag(start, end);
            int n = Math.max(2, Mth.ceil(WireGeometry.samples(WireGeometry.length(start, end, sag))) + 1);
            var points = new ArrayList<Vec3>(n);
            for (int k = 0; k < n; k++) points.add(WireGeometry.sample(start, end, k / (n - 1.0), sag).subtract(cam));

            PowerlineRenderUtil.lineStrip(buffers, pose, points, 1f, 0f, 0f, alpha);
        }
    }
}
