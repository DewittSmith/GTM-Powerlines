package com.github.dewittsmith.gtm_powerlines.client.renderer;

import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class PowerlineWireRenderer {
    private static final ResourceLocation WIRE_TEXTURE = new ResourceLocation("gtceu", "block/cable/insulation_5");

    private static final float BLOCK_PIXEL_SIZE = 16;
    private static final float PIXEL_SIZE = 2;
    private static final float BLOCK_SIZE = PIXEL_SIZE / BLOCK_PIXEL_SIZE;
    private static final float HALF = BLOCK_SIZE / 2;

    public static RenderType wireRenderType() {
        return RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
    }

    public static void renderWire(PoseStack poseStack, MultiBufferSource buffers, Vec3 start, Vec3 end, int argb, int light) {
        VertexConsumer consumer = buffers.getBuffer(wireRenderType());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WIRE_TEXTURE);
        PoseStack.Pose pose = poseStack.last();

        double length = start.distanceTo(end);
        if (length < 1e-4) return;
        double sag = WireGeometry.sag(length);
        int samples = WireGeometry.samples(length);

        Vec3 pPos = WireGeometry.sample(start, end, 0, sag);
        Vec3 pFwd = WireGeometry.sampleTangent(start, end, 0, sag).normalize();
        Vec3 pRef = Math.abs(pFwd.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 pRight = pFwd.cross(pRef).normalize();
        Vec3 pUp = pRight.cross(pFwd).normalize();

        float u0 = sprite.getU0(), v0 = sprite.getV0(), u1 = sprite.getU1(), v1 = sprite.getV1();
        float w = u1 - u0, h = v1 - v0;
        float tw = w / BLOCK_PIXEL_SIZE, th = h / BLOCK_PIXEL_SIZE;
        u0 += tw;
        v0 += th;
        u1 = u0 + tw * PIXEL_SIZE;
        v1 -= th;

        for (int i = 1; i < samples; i++) {
            double t = i / (samples - 1.0);
            Vec3 pos = WireGeometry.sample(start, end, t, sag);
            Vec3 fwd = WireGeometry.sampleTangent(start, end, t, sag).normalize();
            Vec3 ref = Math.abs(fwd.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 right = fwd.cross(ref).normalize();
            Vec3 up = right.cross(fwd).normalize();

            Vec3 pr = pRight.scale(HALF), pu = pUp.scale(HALF);
            Vec3 cr = right.scale(HALF), cu = up.scale(HALF);

            Vec3 pTL = pPos.subtract(pr).add(pu), pTR = pPos.add(pr).add(pu);
            Vec3 pBL = pPos.subtract(pr).subtract(pu), pBR = pPos.add(pr).subtract(pu);
            Vec3 cTL = pos.subtract(cr).add(cu), cTR = pos.add(cr).add(cu);
            Vec3 cBL = pos.subtract(cr).subtract(cu), cBR = pos.add(cr).subtract(cu);

            Vec3 nUp = up.add(pUp).normalize();
            Vec3 nRight = right.add(pRight).normalize();

            // top
            quad(consumer, pose, pTL, cTL, cTR, pTR, argb, u0, v0, u1, v1, light, nUp);
            // right
            quad(consumer, pose, pTR, cTR, cBR, pBR, argb, u0, v0, u1, v1, light, nRight);
            // bottom
            quad(consumer, pose, pBR, cBR, cBL, pBL, argb, u0, v0, u1, v1, light, nUp.reverse());
            // left
            quad(consumer, pose, pBL, cBL, cTL, pTL, argb, u0, v0, u1, v1, light, nRight.reverse());

            pPos = pos;
            pRight = right;
            pUp = up;
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

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p, int argb, float u, float v, int light, Vec3 n) {
        consumer.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
                .color(argb)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), (float) n.x, (float) n.y, (float) n.z)
                .endVertex();
    }
}
