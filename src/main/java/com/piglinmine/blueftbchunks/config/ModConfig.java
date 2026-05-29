package com.piglinmine.blueftbchunks.config;

import com.piglinmine.blueftbchunks.BlueChunks;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = BlueChunks.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue FTB_CHUNKS_ENABLED = BUILDER
            .comment(
                    "Enable/disable FTB Chunks integration for BlueMap.",
                    "If true, claimed chunks will be displayed on the map.",
                    "Default: true"
            )
            .define("ftbChunksEnabled", true);

    private static final ForgeConfigSpec.IntValue CLAIM_UPDATE_INTERVAL_MS = BUILDER
            .comment(
                    "How often to periodically update chunk claims on BlueMap, in milliseconds.",
                    "Lower values update faster but may increase server load.",
                    "Default: 60000 (1 minute)"
            )
            .defineInRange("claimUpdateIntervalMs", 60000, 5000, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue STARTUP_DELAY_MS = BUILDER
            .comment(
                    "How long to wait after server start before loading claims, in milliseconds.",
                    "This delay allows FTB Chunks to fully initialize and load saved claim data.",
                    "Increase this value if claims don't show up on server restart.",
                    "Default: 3000 (3 seconds)"
            )
            .defineInRange("startupDelayMs", 3000, 1000, 60000);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean ftbChunksEnabled = true;
    public static int claimUpdateIntervalMs = 60000;
    public static int startupDelayMs = 3000;

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
    }
}
