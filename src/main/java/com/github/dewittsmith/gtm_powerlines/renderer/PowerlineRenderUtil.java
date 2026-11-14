package com.github.dewittsmith.gtm_powerlines.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.github.dewittsmith.gtm_powerlines.wire.WireGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Small geometry helpers shared by the client renderers. Positions are camera-relative. */
public final class PowerlineRenderUtil {

    /** A plain white texel; tinting it with a vertex colour gives a flat colour. */
    public static final ResourceLocation FLAT_TEXTURE = ResourceLocation.parse("minecraft:block/white_concrete");

    public record UV(float u0, float v0, float u1, float v1) {}

    /** UV of a single texel in the middle of {@link #FLAT_TEXTURE}. */
    public static UV flatUV() {
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FLAT_TEXTURE);
        float u = (sprite.getU0() + sprite.getU1()) / 2, v = (sprite.getV0() + sprite.getV1()) / 2;
        return new UV(u, v, u, v);
    }

    /** UV strip of a GT cable texture, as wide as the cable, like the regular wire renderer uses. */
    public static UV cableUV(ResourceLocation texture, float pixelSize) {
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
        float tw = (sprite.getU1() - sprite.getU0()) / 16, th = (sprite.getV1() - sprite.getV0()) / 16;
        float u0 = sprite.getU0() + tw;
        return new UV(u0, sprite.getV0() + th, u0 + tw * pixelSize, sprite.getV1() - th);
    }

    /**
     * Square tube following the sagging cable from {@code start} to {@code end}.
     *
     * @param inward emit faces pointing into the tube (for back-face-culled "inverted hull" outlines)
     */
    public static void tube(VertexConsumer vc, PoseStack.Pose pose, Vec3 start, Vec3 end, double half, int argb,
                            UV uv, int light, boolean inward) {
        double sag = WireGeometry.sag(start, end);
        int samples = Math.max(2, Mth.ceil(WireGeometry.samples(WireGeometry.length(start, end, sag))) + 1);

        Vec3[] prevRing = null;
        Vec3 prevCenter = null;
        for (int i = 0; i < samples; i++) {
            double t = i / (samples - 1.0);
            Vec3 center = WireGeometry.sample(start, end, t, sag);
            Vec3 forward = WireGeometry.sampleTangent(start, end, t, sag);
            Vec3 ref = Math.abs(forward.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 right = forward.cross(ref).normalize().scale(half);
            Vec3 up = right.cross(forward).normalize().scale(half);
            Vec3[] ring = {
                    center.add(right).add(up),
                    center.subtract(right).add(up),
                    center.subtract(right).subtract(up),
                    center.add(right).subtract(up)
            };

            if (prevRing != null) {
                Vec3 axis = prevCenter.add(center).scale(0.5);
                for (int k = 0; k < 4; k++) {
                    int n = (k + 1) % 4;
                    quad(vc, pose, axis, prevRing[k], prevRing[n], ring[n], ring[k], argb, uv, light, inward);
                }
            }
            prevRing = ring;
            prevCenter = center;
        }
    }

    /** Axis-aligned box with outward faces. */
    public static void box(VertexConsumer vc, PoseStack.Pose pose, AABB box, int argb, UV uv, int light) {
        Vec3 c = box.getCenter();
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        Vec3[] p = {
                new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1),
                new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1)
        };
        quad(vc, pose, c, p[0], p[1], p[2], p[3], argb, uv, light, false); // bottom
        quad(vc, pose, c, p[4], p[5], p[6], p[7], argb, uv, light, false); // top
        quad(vc, pose, c, p[0], p[1], p[5], p[4], argb, uv, light, false); // north
        quad(vc, pose, c, p[3], p[2], p[6], p[7], argb, uv, light, false); // south
        quad(vc, pose, c, p[0], p[3], p[7], p[4], argb, uv, light, false); // west
        quad(vc, pose, c, p[1], p[2], p[6], p[5], argb, uv, light, false); // east
    }

    /**
     * Emits a quad whose front face points away from {@code inside} (or towards it if {@code inward}); the vertex
     * order is fixed up automatically so back-face culling behaves.
     */
    public static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 inside, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                            int argb, UV uv, int light, boolean inward) {
        Vec3 mid = a.add(b).add(c).add(d).scale(0.25);
        Vec3 outward = mid.subtract(inside);
        Vec3 front = b.subtract(a).cross(c.subtract(a));
        boolean facesOut = front.dot(outward) > 0;
        if (facesOut == inward) {
            Vec3 tmp = b;
            b = d;
            d = tmp;
        }
        Vec3 normal = inward ? outward.normalize().reverse() : outward.normalize();
        vertex(vc, pose, a, argb, uv.u0(), uv.v0(), light, normal);
        vertex(vc, pose, b, argb, uv.u0(), uv.v1(), light, normal);
        vertex(vc, pose, c, argb, uv.u1(), uv.v1(), light, normal);
        vertex(vc, pose, d, argb, uv.u1(), uv.v0(), light, normal);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int argb, float u, float v, int light,
                               Vec3 n) {
        vc.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
                .color(argb)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), (float) n.x, (float) n.y, (float) n.z)
                .endVertex();
    }
}
