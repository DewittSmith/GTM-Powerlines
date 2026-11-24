package com.github.dewittsmith.gtm_powerlines;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.github.dewittsmith.gtm_powerlines.config.GTMPowerlinesConfig;
import com.github.dewittsmith.gtm_powerlines.network.Net;
import com.github.dewittsmith.gtm_powerlines.registry.GTMPowerlinesRegistries;
import com.tterrag.registrate.util.entry.RegistryEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(GTMPowerlines.MOD_ID)
@SuppressWarnings("removal")
public class GTMPowerlines {

    public static final String MOD_ID = "gtm_powerlines";
    public static final Logger LOGGER = LogManager.getLogger();
    public static GTRegistrate POWERLINE_REGISTRATE = GTRegistrate.create(GTMPowerlines.MOD_ID);

    public static final RegistryEntry<CreativeModeTab> POWERLINE_CREATIVE_TAB = POWERLINE_REGISTRATE
            .defaultCreativeTab(MOD_ID, builder -> builder
                    .displayItems(new GTCreativeModeTabs.RegistrateDisplayItemsGenerator(MOD_ID, POWERLINE_REGISTRATE))
                    .title(Component.translatable("itemGroup.gtm_powerlines.creative_tab"))
                    .icon(GTMPowerlinesRegistries.MULTIMETER::asStack)
                    .build())
            .register();

    public GTMPowerlines() {
        var context = FMLJavaModLoadingContext.get();
        var modEventBus = context.getModEventBus();

        GTMPowerlinesConfig.init();

        modEventBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(Net::init));

        modEventBus.addGenericListener(MachineDefinition.class, this::registerMachines);

        MinecraftForge.EVENT_BUS.register(this);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        GTMPowerlinesRegistries.init();
    }
}
