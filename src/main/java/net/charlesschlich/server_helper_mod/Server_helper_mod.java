package net.charlesschlich.server_helper_mod;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Server_helper_mod.MOD_ID)
@SuppressWarnings("removal")
public class Server_helper_mod {

    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "server_helper_mod";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    public Server_helper_mod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Server Helper Mod] Loaded successfully");
        LOGGER.info("Restart times: {}", Config.restartTimes);
        LOGGER.info("Warning minutes: {}", Config.warnMinutes);
        LOGGER.info("Messages enabled: {}", Config.enableMessages);
        LOGGER.info("Command to execute: {}", Config.commandToExecute);
        LOGGER.info("Execute at Zero: {}", Config.executeAtZero);

        RestartScheduler.resetSchedule(event.getServer());
    }

}
