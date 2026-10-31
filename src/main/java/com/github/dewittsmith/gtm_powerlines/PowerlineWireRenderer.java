package com.github.dewittsmith.gtm_powerlines;

import com.github.dewittsmith.gtm_powerlines.machines.PowerlineInsulatorBlockEntity;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

public class PowerlineWireRenderer implements BlockEntityRenderer<PowerlineInsulatorBlockEntity> {
    public static void renderWire(PoseStack poseStack, VertexConsumer consumer, Vec3 start, Vec3 end, int argb) {
        PoseStack.Pose pose = poseStack.last();

        double length = start.distanceTo(end);
        double sag = WireGeometry.sag(length);
        int samples = WireGeometry.samples(length);

        Vec3 prev = WireGeometry.sample(start, end, 0f, sag);
        for (int i = 1; i < samples; ++i) {
            Vec3 pos = WireGeometry.sample(start, end, i / (samples - 1f), sag);
            Vec3 dir = pos.subtract(prev).normalize();

            consumer.vertex(pose.pose(), (float) prev.x, (float) prev.y, (float) prev.z)
                    .color(argb)
                    .normal(pose.normal(), (float) dir.x, (float) dir.y, (float) dir.z)
                    .endVertex();
            consumer.vertex(pose.pose(), (float) pos.x, (float) pos.y, (float) pos.z)
                    .color(argb)
                    .normal(pose.normal(), (float) dir.x, (float) dir.y, (float) dir.z)
                    .endVertex();

            prev = pos;
        }
    }

    public static int wireColor(ItemLike item) {
        if (item == null || item == Items.AIR) return DEFAULT_COLOR;
        var stack = ChemicalHelper.getMaterialEntry(item).material();
        return 0xFF000000 | stack.getMaterialRGB();
    }

    public static int wireColor(ResourceLocation wireId) {
        var item = ForgeRegistries.ITEMS.getValue(wireId);
        return wireColor(item);
    }

    private static final int DEFAULT_COLOR = 0xFF000000;

    public PowerlineWireRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRender(PowerlineInsulatorBlockEntity blockEntity, Vec3 cameraPos) {
        return true;
    }

    @Override
    public boolean shouldRenderOffScreen(PowerlineInsulatorBlockEntity blockEntity) {
        return true;
    }

    @Override
    public void render(PowerlineInsulatorBlockEntity insulatorBlock, float partialTick, PoseStack poseStack, MultiBufferSource multiBufferSource, int light, int overlay) {
        VertexConsumer consumer = multiBufferSource.getBuffer(RenderType.lines());
        BlockPos origin = insulatorBlock.getBlockPos();

        var level = insulatorBlock.getLevel();
        if (level == null) return;

        for (PowerlineInsulatorBlockEntity.Connection connection : insulatorBlock.getConnections()) {
            var target = connection.target();
            if (origin.compareTo(target) >= 0) continue;

            var start = WireGeometry.getAttachmentPoint(insulatorBlock);
            var end = WireGeometry.getAttachmentPoint(level, target);

            start = start.subtract(Vec3.atLowerCornerOf(origin));
            end = end.subtract(Vec3.atLowerCornerOf(origin));

            renderWire(poseStack, consumer, start, end, wireColor(connection.wire()));
        }
    }
}
