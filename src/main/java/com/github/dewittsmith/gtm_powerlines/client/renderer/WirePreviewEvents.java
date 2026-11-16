package com.github.dewittsmith.gtm_powerlines.client.renderer;

import com.gregtechceu.gtceu.api.blockentity.IPaintable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.wire.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.ArrayList;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, value = Dist.CLIENT)
public class WirePreviewEvents {

    private static final double OUTLINE_MARGIN = 1 / 32.0;
    private static final ResourceLocation OUTLINE_TEXTURE = ResourceLocation.parse("minecraft:block/white_concrete");

    private static final int SOLVENT_OUTLINE_COLOR = 0xFFFFFF;
    private static final ResourceLocation CABLE_TEXTURE = ResourceLocation.parse("gtceu:block/cable/insulation_5");

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;

        if (player == null || level == null) return;

        renderWireTarget(event, mc, player);
        renderDraggingWire(event, mc, player, level);
        renderMultimeterPath(event, mc);
    }

    @SubscribeEvent
    public static void onHighlightBlock(RenderHighlightEvent.Block event) {
        var player = Minecraft.getInstance().player;
        if (player != null && targetedWire(player, event.getPartialTick()) != null) event.setCanceled(true);
    }

    private static @Nullable WireTargeting.Hit targetedWire(LocalPlayer player, float partialTick) {
        if (!WireTools.targetsWires(player.getMainHandItem())) return null;
        return WireTargeting.pick(ClientWires.graph(), player, partialTick);
    }

    private static void renderDraggingWire(RenderLevelStageEvent event, Minecraft mc, LocalPlayer player, Level level) {
        var attachment = WireAttachment.get(player);
        if (attachment == null || !attachment.dimension().equals(level.dimension())) return;
        if (!player.getMainHandItem().is(attachment.wire()) && !player.getOffhandItem().is(attachment.wire())) return;
        if (!(level.getBlockEntity(attachment.pos()) instanceof PowerlineInsulatorBlockEntity)) return;

        var start = WireGeometry.getAttachmentPoint(level, attachment.pos(), attachment.stats());
        var cameraPos = event.getCamera().getPosition();

        if (mc.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK &&
                !blockHit.getBlockPos().equals(attachment.pos()) &&
                level.getBlockEntity(blockHit.getBlockPos()) instanceof PowerlineInsulatorBlockEntity) {
            var target = WireGeometry.getAttachmentPoint(level, blockHit.getBlockPos(), attachment.stats());
            renderConnectionPreview(event, mc, level, start, target, attachment.stats());
            return;
        }

        var end = player.getRopeHoldPosition(event.getPartialTick());

        var poseStack = event.getPoseStack();
        var bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        var wireInfo = attachment.stats();
        var light = LevelRenderer.getLightColor(level, BlockPos.containing(end));

        if (GTMPowerlinesConfig.INSTANCE.rendering.useFlatWires)
            PowerlineWireRenderer.renderBillboard(poseStack, bufferSource, start, end, light, cameraPos, wireInfo);
        else
            PowerlineWireRenderer.renderTube(poseStack, bufferSource, start, end, light, PowerlineWireRenderer.Caps.END,
                    wireInfo);

        poseStack.popPose();
        bufferSource.endBatch(PowerlineWireRenderer.wireRenderType());
    }

    private static void renderWireTarget(RenderLevelStageEvent event, Minecraft mc, LocalPlayer player) {
        var hit = targetedWire(player, event.getPartialTick());
        if (hit == null) return;

        var spray = WireTools.sprayColor(player.getMainHandItem());
        if (spray.isPresent()) {
            var color = spray.getAsInt() == IPaintable.UNPAINTED_COLOR ? SOLVENT_OUTLINE_COLOR : spray.getAsInt();
            renderSprayOutline(event, mc, hit.edge(), color);
        } else {
            renderCutSquare(event, mc, hit);
        }
    }

    private static void renderConnectionPreview(RenderLevelStageEvent event, Minecraft mc, Level level, Vec3 start,
                                                Vec3 end, WireStats stats) {
        Vec3 cam = event.getCamera().getPosition();
        var pose = event.getPoseStack().last();
        var buffers = mc.renderBuffers().bufferSource();

        var type = RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
        var pixelSize = WireGeometry.pixelSize(stats);
        int argb = (stats.color() & 0x00FFFFFF) | 0x80000000;
        PowerlineRenderUtil.tube(buffers.getBuffer(type), pose, start.subtract(cam), end.subtract(cam),
                pixelSize / 32.0, argb, PowerlineRenderUtil.cableUV(CABLE_TEXTURE, pixelSize),
                LightTexture.FULL_BRIGHT, false);
        buffers.endBatch(type);

        if (!WireObstruction.enabled()) return;
        var blocked = WireObstruction.find(level, start, end);
        if (blocked.isEmpty()) return;

        var consumer = buffers.getBuffer(PowerlineRenderTypes.LINES_ON_TOP);
        for (var pos : blocked) {
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            var box = (shape.isEmpty() ? new AABB(BlockPos.ZERO) : shape.bounds())
                    .move(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z).inflate(0.002);
            LevelRenderer.renderLineBox(event.getPoseStack(), consumer, box, 1f, 0f, 0f, 1f);
        }
        buffers.endBatch(PowerlineRenderTypes.LINES_ON_TOP);
    }

    private static void renderCutSquare(RenderLevelStageEvent event, Minecraft mc, WireTargeting.Hit hit) {
        Vec3 cam = event.getCamera().getPosition();
        Vec3 forward = hit.tangent();
        Vec3 ref = Math.abs(forward.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = forward.cross(ref).normalize();
        Vec3 up = right.cross(forward).normalize();

        double half = (WireGeometry.pixelSize(hit.edge().stats()) + 0.02f) / 16 / 2;
        Vec3 r = right.scale(half), u = up.scale(half);
        Vec3 center = hit.point().subtract(cam);
        Vec3 c0 = center.subtract(r).subtract(u);
        Vec3 c1 = center.add(r).subtract(u);
        Vec3 c2 = center.add(r).add(u);
        Vec3 c3 = center.subtract(r).add(u);

        var buffers = mc.renderBuffers().bufferSource();
        var consumer = buffers.getBuffer(RenderType.lines());
        var pose = event.getPoseStack().last();
        line(consumer, pose, c0, c1, 1f, 0f, 0f, 1f);
        line(consumer, pose, c1, c2, 1f, 0f, 0f, 1f);
        line(consumer, pose, c2, c3, 1f, 0f, 0f, 1f);
        line(consumer, pose, c3, c0, 1f, 0f, 0f, 1f);
        buffers.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, Vec3 from, Vec3 to, float r, float g,
                             float b, float a) {
        var n = to.subtract(from).normalize();
        consumer.vertex(pose.pose(), (float) from.x, (float) from.y, (float) from.z).color(r, g, b, a)
                .normal(pose.normal(), (float) n.x, (float) n.y, (float) n.z).endVertex();
        consumer.vertex(pose.pose(), (float) to.x, (float) to.y, (float) to.z).color(r, g, b, a)
                .normal(pose.normal(), (float) n.x, (float) n.y, (float) n.z).endVertex();
    }

    private static void renderSprayOutline(RenderLevelStageEvent event, Minecraft mc, PowerlineGraph.Edge edge,
                                           int rgb) {
        var stats = edge.stats();
        Vec3 cam = event.getCamera().getPosition();
        Vec3 start = WireGeometry.getAttachmentPoint(edge.a(), edge.dirA(), stats).subtract(cam);
        Vec3 end = WireGeometry.getAttachmentPoint(edge.b(), edge.dirB(), stats).subtract(cam);
        var pixelSize = WireGeometry.pixelSize(stats);
        float halfSize = (float) (pixelSize / 32.0 + OUTLINE_MARGIN);

        var sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(OUTLINE_TEXTURE);
        var buffers = mc.renderBuffers().bufferSource();
        var consumer = buffers.getBuffer(RenderType.solid());
        PowerlineWireRenderer.renderTube(event.getPoseStack(), consumer, start, end, LightTexture.FULL_BRIGHT,
                PowerlineWireRenderer.Caps.NONE, sprite, pixelSize, halfSize, 0xFF000000 | rgb, true);
        buffers.endBatch(RenderType.solid());
    }

    private static void renderMultimeterPath(RenderLevelStageEvent event, Minecraft mc) {
        var insulators = MultimeterHighlight.get();
        if (insulators == null) return;

        Vec3 cam = event.getCamera().getPosition();
        var graph = ClientWires.graph();
        var points = new ArrayList<Vec3>(insulators.size());
        for (int i = 0; i < insulators.size(); i++) {
            var pos = insulators.get(i);
            var neighbor = insulators.get(i == 0 ? 1 : i - 1);
            points.add(attachmentPoint(graph, pos, neighbor).subtract(cam));
        }

        PowerlineRenderUtil.lineStrip(mc.renderBuffers().bufferSource(), PowerlineRenderTypes.LINE_STRIP_ON_TOP,
                event.getPoseStack().last(), points, 1f, 0.85f, 0f, 1f);
    }

    private static Vec3 attachmentPoint(PowerlineGraph graph, BlockPos pos, BlockPos neighbor) {
        var edge = graph.getEdge(pos, neighbor).orElse(null);
        if (edge == null || edge.stats() == null) return pos.getCenter();
        return WireGeometry.getAttachmentPoint(pos, edge.dirAt(pos), edge.stats());
    }
}
