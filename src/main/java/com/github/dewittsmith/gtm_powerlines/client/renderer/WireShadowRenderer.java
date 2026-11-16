package com.github.dewittsmith.gtm_powerlines.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.graph.PowerlineGraph;
import com.github.dewittsmith.gtm_powerlines.wire.ClientWires;
import com.github.dewittsmith.gtm_powerlines.wire.WireAttachment;
import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Set;

public final class WireShadowRenderer {

    private static final double LIGHT_DISTANCE = 100_000;

    private static final Set<PowerlineGraph.Edge> RENDERED = new ReferenceOpenHashSet<>();

    private WireShadowRenderer() {}

    private static Vec3 lightDirection(PoseStack modelView) {
        var dir = new Matrix4f(modelView.last().pose()).invert().transformDirection(new Vector3f(0, 0, 1)).normalize();
        return new Vec3(dir.x(), dir.y(), dir.z());
    }

    public static void render(PoseStack modelView, MultiBufferSource buffers) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 light = cam.add(lightDirection(modelView).scale(LIGHT_DISTANCE));
        double maxDistance = GTMPowerlinesConfig.INSTANCE.rendering.flatWireLODDistance;
        int packedLight = LightTexture.FULL_BRIGHT;

        modelView.pushPose();
        modelView.translate(-cam.x, -cam.y, -cam.z);

        RENDERED.clear();
        for (var chunk : ClientWires.byChunk().long2ObjectEntrySet()) {
            var key = chunk.getLongKey();
            if (!level.getChunkSource().hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key))) continue;

            for (var edge : chunk.getValue()) {
                if (!RENDERED.add(edge)) continue;
                var stats = edge.stats();
                if (stats == null) continue;

                var a = WireGeometry.getAttachmentPoint(edge.a(), edge.dirA(), stats);
                var b = WireGeometry.getAttachmentPoint(edge.b(), edge.dirB(), stats);
                if (WireLOD.getLOD(WireGeometry.distanceToSegment(cam, a, b)) == WireLOD.CULL) continue;

                PowerlineWireRenderer.renderBillboard(modelView, buffers, a, b, packedLight, light, stats,
                        edge.renderColor());
            }
        }
        RENDERED.clear();

        var tieConsumer = buffers.getBuffer(PowerlineWireRenderer.wireRenderType());
        for (long key : ClientWires.graph().nodes()) {
            var pos = BlockPos.of(key);
            if (pos.distToCenterSqr(cam) > maxDistance * maxDistance) continue;
            if (!level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof PowerlineInsulatorBlockEntity insulator)) continue;
            ClientWireRenderer.renderTie(level, insulator, null, Vec3.ZERO, modelView.last(), tieConsumer);
        }

        var player = mc.player;
        var attachment = player == null ? null : WireAttachment.get(player);
        if (attachment != null && attachment.dimension().equals(level.dimension()) &&
                (player.getMainHandItem().is(attachment.wire()) || player.getOffhandItem().is(attachment.wire())) &&
                level.getBlockEntity(attachment.pos()) instanceof PowerlineInsulatorBlockEntity) {
            var start = WireGeometry.getAttachmentPoint(level, attachment.pos(), attachment.stats());
            var end = player.getRopeHoldPosition(mc.getPartialTick());
            PowerlineWireRenderer.renderBillboard(modelView, buffers, start, end, packedLight, light,
                    attachment.stats());
        }

        modelView.popPose();
    }
}
