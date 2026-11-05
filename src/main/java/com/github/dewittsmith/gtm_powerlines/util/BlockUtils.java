package com.github.dewittsmith.gtm_powerlines.util;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class BlockUtils {

    public static <T> Optional<T> instanceOf(Level level, BlockPos pos, Class<T> type) {
        if (level == null || pos == null) return Optional.empty();
        var entity = level.getBlockEntity(pos);
        if (type.isInstance(entity)) return Optional.of(type.cast(entity));
        if (entity instanceof MetaMachineBlockEntity meta && type.isInstance(meta.metaMachine))
            return Optional.of(type.cast(meta.metaMachine));
        return Optional.empty();
    }

//    public static <T> Optional<T> instanceOf() {
//
//    }
}
