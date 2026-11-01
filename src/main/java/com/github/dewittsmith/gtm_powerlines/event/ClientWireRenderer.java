package com.github.dewittsmith.gtm_powerlines.event;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.client.renderer.PowerlineWireRenderer;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
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

        var maxWireRangeSq = GTMPowerlinesConfig.INSTANCE.maxWireRange * GTMPowerlinesConfig.INSTANCE.maxWireRange;
        for (PowerlineNetwork.Edge e : ClientWires.all()) {
            Vec3 a = WireGeometry.getAttachmentPoint(level, e.a()), b = WireGeometry.getAttachmentPoint(level, e.b());
            Vec3 mid = a.add(b).scale(0.5);

            if (mid.distanceToSqr(cam) > maxWireRangeSq + a.distanceToSqr(b)) continue;
            if (!frustum.isVisible(new AABB(a, b).inflate(1.0))) continue;

            int light = LevelRenderer.getLightColor(level, BlockPos.containing(mid));

            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            PowerlineWireRenderer.renderWire(pose, buffers, a, b, WireColor.wireColor(e.wire()), light);
            pose.popPose();
        }
        buffers.endBatch(PowerlineWireRenderer.wireRenderType());
    }
}
