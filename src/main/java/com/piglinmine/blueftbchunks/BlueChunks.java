package com.piglinmine.blueftbchunks;

import com.piglinmine.blueftbchunks.bluemap.BlueMapManager;
import com.piglinmine.blueftbchunks.command.ModCommands;
import com.piglinmine.blueftbchunks.config.ModConfig;
import com.piglinmine.blueftbchunks.data.ChunkDataManager;
import com.piglinmine.blueftbchunks.ftbchunks.ClaimCache;
import com.piglinmine.blueftbchunks.ftbchunks.ClaimEventHandler;
import com.piglinmine.blueftbchunks.ftbchunks.PlayerClaimTracker;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
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

    public BlueChunks() {
        this.claimCache = new ClaimCache();
        this.claimEventHandler = new ClaimEventHandler(claimCache);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(PlayerClaimTracker.class);

        ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.SPEC);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
        LOGGER.debug("Registered mod commands");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Starting BlueChunks...");

        ChunkDataManager.getInstance().initialize(event.getServer());

        BlueMapManager.init(event.getServer());

        if (ModConfig.ftbChunksEnabled) {
            claimEventHandler.register();
        } else {
            LOGGER.info("FTB Chunks integration is disabled in config");
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Stopping BlueChunks...");

        ChunkDataManager.getInstance().shutdown();

        BlueMapManager.shutdown();
        claimCache.clear();
        PlayerClaimTracker.clearCache();
    }
}
