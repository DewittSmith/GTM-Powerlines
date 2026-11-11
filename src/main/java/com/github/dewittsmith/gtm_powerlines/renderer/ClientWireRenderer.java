package com.github.dewittsmith.gtm_powerlines.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;
import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.Set;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, value = Dist.CLIENT)
public final class ClientWireRenderer {

    private static final Set<PowerlineGraph.Edge> RENDERED = new ReferenceOpenHashSet<>();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || ClientWires.byChunk().isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        Frustum frustum = event.getFrustum();

        RENDERED.clear();
        for (var chunk : ClientWires.byChunk().long2ObjectEntrySet()) {
            var key = chunk.getLongKey();
            // ClientLevel#hasChunk always returns true
            if (!level.getChunkSource().hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key))) continue;

            for (var e : chunk.getValue()) {
                if (!RENDERED.add(e)) continue;
                renderEdge(e, level, frustum, cam, pose, buffers);
            }
        }
        RENDERED.clear();
        buffers.endBatch(PowerlineWireRenderer.wireRenderType());
    }

    private static void renderEdge(PowerlineGraph.Edge e, Level level, Frustum frustum, Vec3 cam, PoseStack pose,
                                   MultiBufferSource buffers) {
        var wireInfo = e.stats();
        if (wireInfo == null) return;

        Vec3 a = WireGeometry.getAttachmentPoint(e.a(), e.dirA()),
                b = WireGeometry.getAttachmentPoint(e.b(), e.dirB());
        if (!frustum.isVisible(new AABB(a, b).inflate(2.0))) return;

        var distance = WireGeometry.distanceToSegment(cam, a, b);
        var lod = WireLOD.getLOD(distance);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(a.add(b).scale(0.5)));

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        switch (lod) {
            case TUBE -> PowerlineWireRenderer.renderTube(pose, buffers, a, b, wireInfo.color(), light,
                    PowerlineWireRenderer.Caps.NONE);
            case BILLBOARD -> PowerlineWireRenderer.renderBillboard(pose, buffers, a, b, wireInfo.color(), light,
                    cam);
        }

        pose.popPose();
    }
}
