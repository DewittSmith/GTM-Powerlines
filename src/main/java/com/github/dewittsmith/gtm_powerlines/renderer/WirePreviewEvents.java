package com.github.dewittsmith.gtm_powerlines.renderer;

import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.wire.*;
import com.mojang.blaze3d.vertex.PoseStack;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, value = Dist.CLIENT)
public class WirePreviewEvents {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;

        if (player == null || level == null) return;

        renderCutPreview(event, mc, player, level);
        renderDraggingWire(event, mc, player, level);
    }

    private static void renderDraggingWire(RenderLevelStageEvent event, Minecraft mc, LocalPlayer player, Level level) {
        var attachment = WireAttachment.get(player);
        if (attachment == null || !attachment.dimension().equals(level.dimension())) return;
        if (!player.getMainHandItem().is(attachment.wire())) return;
        if (!(level.getBlockEntity(attachment.pos()) instanceof PowerlineInsulatorBlockEntity)) return;

        Vec3 start = WireGeometry.getAttachmentPoint(level, attachment.pos());
        Vec3 end = player.getRopeHoldPosition(event.getPartialTick());
        Vec3 cameraPos = event.getCamera().getPosition();

        PoseStack poseStack = event.getPoseStack();
        var bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        if (GTMPowerlinesConfig.INSTANCE.rendering.useFlatWires) PowerlineWireRenderer.renderBillboard(poseStack,
                bufferSource, start, end, attachment.argb(), LightTexture.FULL_BRIGHT, cameraPos);
        else PowerlineWireRenderer.renderTube(poseStack, bufferSource, start, end, attachment.argb(),
                LightTexture.FULL_BRIGHT, PowerlineWireRenderer.Caps.END);
        poseStack.popPose();
        bufferSource.endBatch(PowerlineWireRenderer.wireRenderType());
    }

    private static void renderCutPreview(RenderLevelStageEvent event, Minecraft mc, LocalPlayer player, Level level) {
        if (!player.getMainHandItem().is(CustomTags.WIRE_CUTTERS)) return;
        var sel = WireCutSelection.get(player);
        if (sel == null || !(level.getBlockEntity(sel.pos()) instanceof PowerlineInsulatorBlockEntity)) return;

        var pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        var bs = mc.renderBuffers().bufferSource();
        var buffer = bs.getBuffer(RenderType.LINES);

        var pos = sel.pos();
        var state = level.getBlockState(pos);
        if (!state.isAir()) {
            var box = state.getShape(level, pos, CollisionContext.empty()).bounds()
                    .move(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            LevelRenderer.renderLineBox(pose, buffer, box, 1f, 1f, 0f, 1f);
        }

        var hit = WireTargeting.find(level, sel.pos(), ClientWires.at(sel.pos()),
                player.getEyePosition(event.getPartialTick()), player.getViewVector(event.getPartialTick()));
        if (hit != null) {
            AABB dot = new AABB(hit.point(), hit.point()).inflate(0.1).move(-cam.x, -cam.y, -cam.z);
            LevelRenderer.renderLineBox(pose, buffer, dot, 1f, 1f, 0f, 1f);
        }

        bs.endBatch(RenderType.lines());
    }
}
