package com.github.dewittsmith.gtm_powerlines.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.block.PowerlineInsulatorBlock;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;
import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import com.github.dewittsmith.gtm_powerlines.wire.WireStats;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.Set;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, value = Dist.CLIENT)
public final class ClientWireRenderer {

    private static final Set<PowerlineGraph.Edge> RENDERED = new ReferenceOpenHashSet<>();
    private static final ResourceLocation TIE_TEXTURE = ResourceLocation.parse("gtceu:block/cable/insulation_5");

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;

        var mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || ClientWires.byChunk().isEmpty()) return;

        var cam = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var frustum = event.getFrustum();

        RENDERED.clear();
        for (var chunk : ClientWires.byChunk().long2ObjectEntrySet()) {
            var key = chunk.getLongKey();
            if (!level.getChunkSource().hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key))) continue;

            for (var e : chunk.getValue()) {
                if (!RENDERED.add(e)) continue;
                renderEdge(e, level, frustum, cam, pose, buffers);
            }
        }
        RENDERED.clear();
        renderTies(level, frustum, cam, pose, buffers);
        buffers.endBatch(PowerlineWireRenderer.wireRenderType());
    }

    private static void renderTies(Level level, Frustum frustum, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
        var graph = ClientWires.graph();
        if (graph.nodes().isEmpty()) return;

        var maxDistance = GTMPowerlinesConfig.INSTANCE.rendering.flatWireLODDistance;
        var consumer = buffers.getBuffer(PowerlineWireRenderer.wireRenderType());

        for (long key : graph.nodes()) {
            var pos = BlockPos.of(key);
            if (pos.distToCenterSqr(cam) > maxDistance * maxDistance) continue;
            if (!level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity insulator)) continue;
            renderTie(level, insulator, frustum, cam, pose.last(), consumer);
        }
    }

    public static void renderTie(Level level, PowerlineInsulatorBlockEntity insulator, @Nullable Frustum frustum,
                                 Vec3 origin, PoseStack.Pose pose, VertexConsumer consumer) {
        var pos = insulator.getBlockPos();
        WireStats widest = null;
        float widestPixels = 0;
        var count = 0;
        long r = 0, g = 0, b = 0;
        for (var edge : ClientWires.graph().atBlock(pos)) {
            if (edge.stats() == null) continue;
            var argb = edge.renderColor();
            r += (argb >> 16) & 0xFF;
            g += (argb >> 8) & 0xFF;
            b += argb & 0xFF;
            count++;
            var pixels = WireGeometry.pixelSize(edge.stats());
            if (widest == null || pixels > widestPixels) {
                widest = edge.stats();
                widestPixels = pixels;
            }
        }
        if (widest == null) return;

        var direction = insulator.getBlockState().getValue(PowerlineInsulatorBlock.DIRECTION);
        var center = WireGeometry.getAttachmentPoint(pos, direction, widest);
        var half = (Math.round(widestPixels) + 1) / 32.0 + 0.002;
        var box = new AABB(center, center).inflate(half);
        if (frustum != null && !frustum.isVisible(box)) return;

        var side = Math.round(widestPixels) + 1;
        var color = 0xFF000000 | (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
        var light = LevelRenderer.getLightColor(level, pos);
        var strip = PowerlineRenderUtil.cableUV(TIE_TEXTURE, side);
        var uv = new PowerlineRenderUtil.UV(strip.u0(), strip.v0(), strip.u1(),
                strip.v0() + (strip.u1() - strip.u0()));
        PowerlineRenderUtil.box(consumer, pose, box.move(-origin.x, -origin.y, -origin.z), color, uv, light);
    }

    private static void renderEdge(PowerlineGraph.Edge e, Level level, Frustum frustum, Vec3 cam, PoseStack pose,
                                   MultiBufferSource buffers) {
        var wireInfo = e.stats();
        if (wireInfo == null) return;

        Vec3 a = WireGeometry.getAttachmentPoint(e.a(), e.dirA(), e.stats()),
                b = WireGeometry.getAttachmentPoint(e.b(), e.dirB(), e.stats());
        var sag = WireGeometry.sag(a, b);
        var bounds = new AABB(a, b).expandTowards(0, -Math.max(0, sag), 0).inflate(0.5);
        if (!frustum.isVisible(bounds)) return;

        var distance = WireGeometry.distanceToSegment(cam, a, b);
        var lod = WireLOD.getLOD(distance);
        if (lod == WireLOD.CULL) return;
        var light = LevelRenderer.getLightColor(level, BlockPos.containing(a.add(b).scale(0.5)));

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        switch (lod) {
            case TUBE -> PowerlineWireRenderer.renderTube(pose, buffers, a, b, light, PowerlineWireRenderer.Caps.NONE,
                    wireInfo, e.renderColor());
            case BILLBOARD -> PowerlineWireRenderer.renderBillboard(pose, buffers, a, b, light, cam, wireInfo,
                    e.renderColor());
            default -> {}
        }

        pose.popPose();
    }
}
