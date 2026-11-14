package com.github.dewittsmith.gtm_powerlines.renderer;

import com.gregtechceu.gtceu.GTCEu;

import com.gregtechceu.gtceu.common.pipelike.cable.Insulation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import com.github.dewittsmith.gtm_powerlines.wire.WireStats;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class PowerlineWireRenderer {

    private static final ResourceLocation WIRE_TEXTURE = GTCEu.id("block/cable/insulation_5");

    public enum Caps {
        NONE,
        START,
        END,
        BOTH;
    }

    public static RenderType wireRenderType() {
        return RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    }

    public static void renderTube(PoseStack poseStack, MultiBufferSource buffers, Vec3 start, Vec3 end,
                                  int light, Caps caps, WireStats stats) {
        var pixelSize = WireGeometry.pixelSize(stats);
        var halfSize = pixelSize / 16f / 2;
        var argb = stats.color();

        VertexConsumer consumer = buffers.getBuffer(wireRenderType());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(WIRE_TEXTURE);
        PoseStack.Pose pose = poseStack.last();

        var sag = WireGeometry.sag(start, end);

        Vec3 pPos = WireGeometry.sample(start, end, 0, sag);
        Vec3 pFwd = WireGeometry.sampleTangent(start, end, 0, sag);
        Vec3 pRef = Math.abs(pFwd.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 pRight = pFwd.cross(pRef).normalize();
        Vec3 pUp = pRight.cross(pFwd).normalize();

        float u0 = sprite.getU0(), v0 = sprite.getV0(), u1 = sprite.getU1(), v1 = sprite.getV1();
        float w = u1 - u0, h = v1 - v0;
        float tw = w / 16, th = h / 16;
        u0 += tw;
        v0 += th;
        u1 = u0 + tw * pixelSize;
        v1 -= th;

        if (caps == Caps.START || caps == Caps.BOTH) {
            Vec3 pr = pRight.scale(halfSize), pu = pUp.scale(halfSize);
            Vec3 pLD = pPos.subtract(pr).subtract(pu), pLU = pPos.subtract(pr).add(pu);
            Vec3 pRD = pPos.add(pr).subtract(pu), pRU = pPos.add(pr).add(pu);
            var cv1 = v0 + th * pixelSize;
            quad(consumer, pose, pLD, pLU, pRU, pRD, argb, u0, v0, u1, cv1, light, pFwd.reverse());
        }

        var length = WireGeometry.length(start, end, sag);
        var samples = WireGeometry.samples(length);

        var t = 0f;
        final var dt = 1 / samples;
        while (samples > 0) {
            var step = Math.min(1, samples);
            t += dt * step;

            Vec3 pos = WireGeometry.sample(start, end, t, sag);
            Vec3 fwd = WireGeometry.sampleTangent(start, end, t, sag).normalize();
            Vec3 ref = Math.abs(fwd.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 right = fwd.cross(ref).normalize();
            Vec3 up = right.cross(fwd).normalize();

            Vec3 pr = pRight.scale(halfSize), pu = pUp.scale(halfSize);
            Vec3 cr = right.scale(halfSize), cu = up.scale(halfSize);

            Vec3 pTL = pPos.subtract(pr).add(pu), pTR = pPos.add(pr).add(pu);
            Vec3 pBL = pPos.subtract(pr).subtract(pu), pBR = pPos.add(pr).subtract(pu);
            Vec3 cTL = pos.subtract(cr).add(cu), cTR = pos.add(cr).add(cu);
            Vec3 cBL = pos.subtract(cr).subtract(cu), cBR = pos.add(cr).subtract(cu);

            Vec3 nUp = up.add(pUp).normalize();
            Vec3 nRight = right.add(pRight).normalize();

            var vEnd = v0 + (v1 - v0) * step;
            // top
            quad(consumer, pose, pTR, cTR, cTL, pTL, argb, u0, v0, u1, vEnd, light, nUp);
            // right
            quad(consumer, pose, pBR, cBR, cTR, pTR, argb, u0, v0, u1, vEnd, light, nRight);
            // bottom
            quad(consumer, pose, pBL, cBL, cBR, pBR, argb, u0, v0, u1, vEnd, light, nUp.reverse());
            // left
            quad(consumer, pose, pTL, cTL, cBL, pBL, argb, u0, v0, u1, vEnd, light, nRight.reverse());

            pPos = pos;
            pRight = right;
            pUp = up;

            samples -= 1;
        }

        if (caps == Caps.END || caps == Caps.BOTH) {
            Vec3 pr = pRight.scale(halfSize), pu = pUp.scale(halfSize);
            Vec3 pLD = pPos.subtract(pr).subtract(pu), pLU = pPos.subtract(pr).add(pu);
            Vec3 pRD = pPos.add(pr).subtract(pu), pRU = pPos.add(pr).add(pu);
            var cv1 = v0 + th * pixelSize;
            quad(consumer, pose, pLD, pLU, pRU, pRD, argb, u0, v0, u1, cv1, light, pFwd);
        }
    }

    public static void renderBillboard(PoseStack poseStack, MultiBufferSource buffers, Vec3 start, Vec3 end,
                                       int light, Vec3 cameraPos, WireStats stats) {
        var pixelSize = WireGeometry.pixelSize(stats);
        var halfSize = pixelSize / 16f / 2;
        var argb = stats.color();

        VertexConsumer consumer = buffers.getBuffer(wireRenderType());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(WIRE_TEXTURE);
        PoseStack.Pose pose = poseStack.last();

        double sag = WireGeometry.sag(start, end);

        float u0 = sprite.getU0(), v0 = sprite.getV0(), u1 = sprite.getU1(), v1 = sprite.getV1();
        float w = u1 - u0, h = v1 - v0;
        float tw = w / 16, th = h / 16;

        u0 += tw;
        v0 += th;
        u1 = u0 + tw * pixelSize;
        v1 -= th;

        Vec3 pPos = WireGeometry.sample(start, end, 0, sag);
        Vec3 pForward = WireGeometry.sampleTangent(start, end, 0, sag);
        Vec3 pToCamera = cameraPos.subtract(pPos).normalize();
        Vec3 pRight = pForward.cross(pToCamera).normalize();

        if (pRight.lengthSqr() < 1.0e-6) {
            Vec3 ref = Math.abs(pForward.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            pRight = pForward.cross(ref).normalize();
        }

        Vec3 pOffset = pRight.scale(halfSize);

        Vec3 pTL = pPos.add(pOffset);
        Vec3 pTR = pPos.subtract(pOffset);

        var length = WireGeometry.length(start, end, sag);
        var samples = WireGeometry.samples(length);

        var t = 0f;
        final var dt = 1 / samples;
        while (samples > 0) {
            var step = Math.min(1, samples);
            t += dt * step;

            Vec3 pos = WireGeometry.sample(start, end, t, sag);
            Vec3 forward = WireGeometry.sampleTangent(start, end, t, sag).normalize();

            Vec3 toCamera = cameraPos.subtract(pos).normalize();
            Vec3 right = forward.cross(toCamera).normalize();

            if (right.lengthSqr() < 1.0e-6) {
                Vec3 ref = Math.abs(forward.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                right = forward.cross(ref).normalize();
            }

            Vec3 offset = right.scale(halfSize);
            Vec3 cTL = pos.add(offset);
            Vec3 cTR = pos.subtract(offset);
            Vec3 normal = right.cross(forward).normalize();

            var vEnd = v0 + (v1 - v0) * step;
            quad(consumer, pose, pTL, cTL, cTR, pTR, argb, u0, v0, u1, vEnd, light, normal);

            pTL = cTL;
            pTR = cTR;

            samples -= 1;
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb,
                             float u0, float v0, float u1, float v1, int light, Vec3 n) {
        vertex(vc, pose, a, argb, u0, v0, light, n);
        vertex(vc, pose, b, argb, u0, v1, light, n);
        vertex(vc, pose, c, argb, u1, v1, light, n);
        vertex(vc, pose, d, argb, u1, v0, light, n);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p, int argb, float u, float v,
                               int light, Vec3 n) {
        consumer.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
                .color(argb)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), (float) n.x, (float) n.y, (float) n.z)
                .endVertex();
    }
}
