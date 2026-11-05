package com.github.dewittsmith.gtm_powerlines.compat.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.github.dewittsmith.gtm_powerlines.GTMPowerlines;
import com.github.dewittsmith.gtm_powerlines.blockentity.PowerlineInsulatorBlockEntity;
import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.machines.PowerlineTowerMachine;
import com.github.dewittsmith.gtm_powerlines.util.BlockUtils;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class PowerlineTowerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity metaMachineBlock)) return;
        if (!(metaMachineBlock.metaMachine instanceof PowerlineTowerMachine towerMachine)) return;
        data.putInt("BlockCount", towerMachine.getBlockCount());
        data.putFloat("MaxRange", towerMachine.getMaxWireDistance());
        data.putInt("MaxConnections", towerMachine.getMaxConnections() * towerMachine.getInsulatorBlocks().size());

        var level = accessor.getLevel();
        var connections = 0;
        var cursor = new BlockPos.MutableBlockPos();
        for (var key : towerMachine.getInsulatorBlocks()) {
            cursor.set(key);
            var insulatorOpt = BlockUtils.instanceOf(level, cursor, PowerlineInsulatorBlockEntity.class);
            if (insulatorOpt.isEmpty()) continue;
            connections += insulatorOpt.get().connectionCount();
        }

        data.putInt("Connections", connections);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        var data = accessor.getServerData();
        if (!data.contains("BlockCount")) return;

        tooltip.add(
                Component.translatable("tooltip.gtm_powerlines.max_distance", Mth.floor(data.getFloat("MaxRange"))));

        if (accessor.getPlayer().isShiftKeyDown()) {
            tooltip.add(Component.translatable("tooltip.gtm_powerlines.connections", data.getInt("Connections"),
                    data.getInt("MaxConnections")));
            tooltip.add(Component.translatable("tooltip.gtm_powerlines.block_count", data.getInt("BlockCount"),
                    GTMPowerlinesConfig.INSTANCE.tower.maxTowerBlocks));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return new ResourceLocation(GTMPowerlines.MOD_ID, "powerline_tower_info");
    }
}
