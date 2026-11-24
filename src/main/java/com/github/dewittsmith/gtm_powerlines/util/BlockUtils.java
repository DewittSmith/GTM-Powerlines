package com.github.dewittsmith.gtm_powerlines.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.util.Optional;

public final class BlockUtils {

    public static <T> Optional<T> instanceOf(Level level, BlockPos pos, Class<T> type) {
        if (level == null || pos == null) return Optional.empty();
        if (!level.isLoaded(pos)) return Optional.empty();
        var entity = level.getBlockEntity(pos);
        if (type.isInstance(entity)) return Optional.of(type.cast(entity));
        return Optional.empty();
    }

    public static <T> Optional<T> instanceOf(ServerLevel level, BlockPos pos, Class<T> type, boolean forceLoad) {
        if (level == null || pos == null) return Optional.empty();
        if (!level.isLoaded(pos)) {
            if (forceLoad) {
                var chunkPos = new ChunkPos(pos);
                level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);
            } else return Optional.empty();
        }
        return instanceOf(level, pos, type);
    }
}
