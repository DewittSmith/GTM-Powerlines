package com.github.dewittsmith.gtm_powerlines.renderer;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.network.TowerHighlightPacket;
import it.unimi.dsi.fastutil.longs.Long2LongArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
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
    private record QueueDepthPos(long pos, int depth) {
    }

    private static final int FADE_IN = 5;
    private static final int FADE_OUT = 20;
    private static final int MAX_TICK_OFFSET = 20;
    private static final int HIGHLIGHT_DURATION = 200;

    private static ResourceKey<Level> dimension;
    private static long[] structure = new long[0], insulators = new long[0];
    private static final Long2LongArrayMap offsetMap = new Long2LongArrayMap();
    private static long expires;
    private static long startTick;

    public static void set(TowerHighlightPacket p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        dimension = p.dimension();
        structure = p.structure();
        insulators = p.insulators();
        startTick = level.getGameTime();
        expires = startTick + HIGHLIGHT_DURATION;

        offsetMap.clear();
        var queue = new ObjectArrayFIFOQueue<>();
        queue.enqueue(new QueueDepthPos(p.controller(), 0));
        while (!queue.isEmpty()) {
            var current = (QueueDepthPos) queue.dequeue();
            if (offsetMap.containsKey(current.pos) || Arrays.stream(structure).noneMatch(l -> l == current.pos)) {
                continue;
            }

            offsetMap.put(current.pos, Math.min(current.depth, MAX_TICK_OFFSET));

            var pos = BlockPos.of(current.pos);
            for (var dir : Direction.values()) {
                queue.enqueue(new QueueDepthPos(pos.relative(dir).asLong(), current.depth + 1));
            }
        }
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
        var lines = buffers.getBuffer(RenderType.LINES);
        var stack = event.getPoseStack();

        var now = level.getGameTime() + event.getPartialTick();
        var elapsed = now - startTick;
        var remaining = expires - now;

        var fadeOut = Mth.clamp(remaining / FADE_OUT, 0f, 1f);

        for (var key : structure) {
            var pos = BlockPos.of(key);
            var state = level.getBlockState(pos);
            var shape = state.getShape(level, pos);
            var box = (shape.isEmpty() ? Shapes.block() : shape).bounds()
                    .move(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);

            var offset = offsetMap.getOrDefault(key, 0L);
            var fadeIn = Mth.clamp((elapsed - offset) / FADE_IN, 0f, 1f);
            var alpha = fadeIn * fadeOut;

            if (state.isAir()) LevelRenderer.renderLineBox(stack, lines, box, 1f, 0f, 0f, alpha);
            else if (Arrays.stream(insulators).anyMatch(x -> x == key))
                LevelRenderer.renderLineBox(stack, lines, box, 1f, 1f, 0f, alpha);
            else LevelRenderer.renderLineBox(stack, lines, box, 0f, 1f, 0f, alpha);
        }

        buffers.endBatch(RenderType.LINES);
    }
}

