package com.github.dewittsmith.gtm_powerlines.renderer;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.network.TowerHighlightPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

@Mod.EventBusSubscriber(modid = GTMPowerlines.MOD_ID, value = Dist.CLIENT)
public final class ClientTowerHighlight {
    private static ResourceKey<Level> dimension;
    private static long[] structure = new long[0], insulators = new long[0];
    private static long expires;

    public static void set(TowerHighlightPacket p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        dimension = p.dimension();
        structure = p.structure();
        insulators = p.insulators();
        expires = level.getGameTime() + p.ticks();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        expires = 0;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || !level.dimension().equals(dimension) || level.getGameTime() > expires) return;

        Vec3 cam = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        var stack = event.getPoseStack();

        for (long structure : structure) {
            var pos = BlockPos.of(structure);
            var state = level.getBlockState(pos);
            var shape = state.getShape(level, pos);
            var box = (shape.isEmpty() ? Shapes.block() : shape).bounds()
                    .move(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);

            if (state.isAir()) LevelRenderer.renderLineBox(stack, lines, box, 1f, 0f, 0f, 1f);
            else if (Arrays.stream(insulators).anyMatch(x -> x == structure))
                LevelRenderer.renderLineBox(stack, lines, box, 1f, 1f, 0f, 1f);
            else LevelRenderer.renderLineBox(stack, lines, box, 0f, 1f, 0f, 1f);
        }

        buffers.endBatch(RenderType.lines());
    }
}

