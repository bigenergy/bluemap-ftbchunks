package com.piglinmine.blueftbchunks;

import com.piglinmine.blueftbchunks.bluemap.BlueMapManager;
import com.piglinmine.blueftbchunks.command.ModCommands;
import com.piglinmine.blueftbchunks.config.ModConfig;
import com.piglinmine.blueftbchunks.data.ChunkDataManager;
import com.piglinmine.blueftbchunks.ftbchunks.ClaimCache;
import com.piglinmine.blueftbchunks.ftbchunks.ClaimEventHandler;
import com.piglinmine.blueftbchunks.ftbchunks.PlayerClaimTracker;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * Main mod class for FTB Chunks BlueMap integration.
 *
 * <p>This mod displays FTB Chunks claim data on BlueMap web maps. It listens for
 * server lifecycle events and coordinates the initialization and shutdown of
 * the BlueMap integration and claim event handlers.</p>
 */
@Mod(BlueChunks.MOD_ID)
public class BlueChunks {

    public static final String MOD_ID = "blueftbchunks";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ClaimCache claimCache;
    private final ClaimEventHandler claimEventHandler;

    /**
     * Constructs the mod instance and registers event listeners.
     *
     * @param modEventBus  the mod's event bus
     * @param modContainer the mod container
     */
    public BlueChunks(IEventBus modEventBus, ModContainer modContainer) {
        this.claimCache = new ClaimCache();
        this.claimEventHandler = new ClaimEventHandler(claimCache);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(PlayerClaimTracker.class);

        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);
    }

    /**
     * Registers mod commands.
     *
     * @param event the command registration event
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
        LOGGER.debug("Registered mod commands");
    }

    /**
     * Handles server startup by initializing the BlueMap integration and registering event handlers.
     *
     * @param event the server starting event
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (ModConfig.enableLogs) LOGGER.info("Starting BlueChunks...");

        ChunkDataManager.getInstance().initialize(event.getServer());

        BlueMapManager.init(event.getServer());

        if (ModConfig.ftbChunksEnabled) {
            claimEventHandler.register();
        } else {
            if (ModConfig.enableLogs) LOGGER.info("FTB Chunks integration is disabled in config");
        }
    }

    /**
     * Handles server shutdown by cleaning up resources.
     *
     * @param event the server stopping event
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (ModConfig.enableLogs) LOGGER.info("Stopping BlueChunks...");

        ChunkDataManager.getInstance().shutdown();

        BlueMapManager.shutdown();
        claimCache.clear();
        PlayerClaimTracker.clearCache();
    }
}