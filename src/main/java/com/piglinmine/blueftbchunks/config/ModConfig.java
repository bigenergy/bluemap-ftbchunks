package com.piglinmine.blueftbchunks.config;

import com.piglinmine.blueftbchunks.BlueChunks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Handles the mod's configuration settings.
 *
 * <p>Configuration values are loaded from the common config file and can be
 * accessed via the public static fields after the config event fires.</p>
 *
 * <p>Available settings:</p>
 * <ul>
 *   <li>{@link #ftbChunksEnabled} - Enable/disable FTB Chunks integration</li>
 *   <li>{@link #claimUpdateIntervalMs} - Interval between periodic claim updates</li>
 *   <li>{@link #startupDelayMs} - Delay before first claim update after server start</li>
 * </ul>
 */
@EventBusSubscriber(modid = BlueChunks.MOD_ID)
public final class ModConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue FTB_CHUNKS_ENABLED = BUILDER
            .comment(
                    "Enable/disable FTB Chunks integration for BlueMap.",
                    "If true, claimed chunks will be displayed on the map.",
                    "Default: true"
            )
            .define("ftbChunksEnabled", true);

    private static final ModConfigSpec.IntValue CLAIM_UPDATE_INTERVAL_MS = BUILDER
            .comment(
                    "How often to periodically update chunk claims on BlueMap, in milliseconds.",
                    "Lower values update faster but may increase server load.",
                    "Default: 60000 (1 minute)"
            )
            .defineInRange("claimUpdateIntervalMs", 60000, 5000, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue STARTUP_DELAY_MS = BUILDER
            .comment(
                    "How long to wait after server start before loading claims, in milliseconds.",
                    "This delay allows FTB Chunks to fully initialize and load saved claim data.",
                    "Increase this value if claims don't show up on server restart.",
                    "Default: 3000 (3 seconds)"
            )
            .defineInRange("startupDelayMs", 3000, 1000, 60000);

    private static final ModConfigSpec.BooleanValue ENABLE_LOGS = BUILDER
            .comment(
                    "Enable verbose INFO logging from this mod.",
                    "Errors and warnings are always logged regardless of this setting.",
                    "Default: false"
            )
            .define("enableLogs", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** Whether FTB Chunks integration is enabled. */
    public static boolean ftbChunksEnabled = true;

    /** Interval between periodic claim updates in milliseconds. */
    public static int claimUpdateIntervalMs = 60000;

    /** Delay before first claim update after server start in milliseconds. */
    public static int startupDelayMs = 3000;

    /** Whether verbose INFO logging is enabled. */
    public static boolean enableLogs = false;

    private ModConfig() {
        throw new AssertionError("Configuration class cannot be instantiated");
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (!event.getConfig().getSpec().equals(SPEC)) {
            return;
        }

        ftbChunksEnabled = FTB_CHUNKS_ENABLED.get();
        claimUpdateIntervalMs = CLAIM_UPDATE_INTERVAL_MS.get();
        startupDelayMs = STARTUP_DELAY_MS.get();
        enableLogs = ENABLE_LOGS.get();
    }
}