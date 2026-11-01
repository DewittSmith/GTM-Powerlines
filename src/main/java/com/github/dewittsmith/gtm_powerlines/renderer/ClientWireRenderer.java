package com.github.dewittsmith.gtm_powerlines.renderer;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.network.PowerlineNetwork;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;
import com.github.dewittsmith.gtm_powerlines.wire.WireColor;
import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, value = Dist.CLIENT)
public final class ClientWireRenderer {
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || ClientWires.all().isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        Frustum frustum = event.getFrustum();

        for (PowerlineNetwork.Edge e : ClientWires.all()) {
            Vec3 a = WireGeometry.getAttachmentPoint(level, e.a()), b = WireGeometry.getAttachmentPoint(level, e.b());
            if (!frustum.isVisible(new AABB(a, b).inflate(1.0))) continue;

            var distance = WireGeometry.distanceToSegment(cam, a, b);
            var lod = WireLOD.getLOD(distance);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(a.add(b).scale(0.5)));

            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);

            switch (lod) {
                case TUBE -> PowerlineWireRenderer.renderTube(pose, buffers, a, b, WireColor.wireColor(e.wire()), light, PowerlineWireRenderer.Caps.NONE);
                case BILLBOARD -> PowerlineWireRenderer.renderBillboard(pose, buffers, a, b, WireColor.wireColor(e.wire()), light, cam);
            }

            pose.popPose();
        }
        buffers.endBatch(PowerlineWireRenderer.wireRenderType());
    }
}
