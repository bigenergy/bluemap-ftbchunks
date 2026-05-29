package com.piglinmine.blueftbchunks.ftbchunks;

import com.piglinmine.blueftbchunks.config.ModConfig;
import com.piglinmine.blueftbchunks.ftbchunks.region.RegionGrouper;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the integration between FTB Chunks and BlueMap.
 *
 * <p>This class handles the lifecycle of the periodic claim update task and maintains
 * team touch time data for overlap resolution. It serves as the main coordination
 * point for synchronizing FTB Chunks claim data with BlueMap markers.</p>
 *
 * <p>The integration uses a scheduled executor to periodically update claims across
 * all dimensions, with configurable startup delay and update interval.</p>
 */
public final class FTBChunksIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "FTBChunks-BlueMap-Updater");
                thread.setDaemon(true);
                return thread;
            }
    );

    private static final Map<String, Map<UUID, Long>> teamTouchTimes = new ConcurrentHashMap<>();
    private static final AtomicBoolean running = new AtomicBoolean(false);

    private static ScheduledFuture<?> updateTask;
    private static MinecraftServer server;

    private FTBChunksIntegration() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Initializes the FTB Chunks integration with the given server instance.
     *
     * <p>This method checks if the integration is enabled in the config and starts
     * the periodic update task if so. It should be called during server startup.</p>
     *
     * @param minecraftServer the Minecraft server instance; must not be null
     */
    public static void init(MinecraftServer minecraftServer) {
        if (!ModConfig.ftbChunksEnabled) {
            LOGGER.info("FTB Chunks integration is disabled in config");
            return;
        }

        if (minecraftServer == null) {
            LOGGER.error("Cannot initialize FTB Chunks integration: server is null");
            return;
        }

        server = minecraftServer;
        start();
    }

    /**
     * Starts the periodic claim update task.
     *
     * <p>The task will begin after a configurable startup delay to allow FTB Chunks
     * to fully load, then run at the configured interval.</p>
     */
    public static void start() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.warn("FTB Chunks integration already running");
            return;
        }

        int intervalMs = Math.max(5000, ModConfig.claimUpdateIntervalMs);
        int initialDelayMs = Math.max(1000, ModConfig.startupDelayMs);

        updateTask = SCHEDULER.scheduleAtFixedRate(
                FTBChunksIntegration::performPeriodicUpdate,
                initialDelayMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );

        LOGGER.info("FTB Chunks integration started (startup delay: {}ms, update interval: {}ms)",
                initialDelayMs, intervalMs);
    }

    /**
     * Stops the periodic update task and clears all cached data.
     * Should be called during server shutdown.
     */
    public static void stop() {
        if (!running.compareAndSet(true, false)) {
            LOGGER.debug("FTB Chunks integration not running");
            return;
        }

        cancelUpdateTask();
        clearAllCaches();

        server = null;
        LOGGER.info("FTB Chunks integration stopped");
    }

    /**
     * Records the current time as the "touch time" for a team in a dimension.
     *
     * <p>Touch times are used for overlap resolution - when multiple teams claim
     * the same chunk, the team with the most recent touch time takes precedence.</p>
     *
     * @param dimensionId the dimension ID as a string (e.g., "minecraft:overworld")
     * @param teamId      the UUID of the team
     */
    public static void updateTeamTouchTime(String dimensionId, UUID teamId) {
        if (dimensionId == null || teamId == null) {
            return;
        }

        teamTouchTimes
                .computeIfAbsent(dimensionId, k -> new ConcurrentHashMap<>())
                .put(teamId, System.nanoTime());
    }

    /**
     * Retrieves all touch times for teams in a dimension.
     *
     * @param dimensionId the dimension ID to query
     * @return an unmodifiable map of team UUIDs to their touch times; never null
     */
    public static Map<UUID, Long> getTouchTimes(String dimensionId) {
        if (dimensionId == null) {
            return Collections.emptyMap();
        }

        Map<UUID, Long> times = teamTouchTimes.get(dimensionId);
        return times != null ? Collections.unmodifiableMap(times) : Collections.emptyMap();
    }

    /**
     * Clears the touch time data for a specific team in a dimension.
     *
     * @param dimensionId the dimension ID
     * @param teamId      the UUID of the team
     */
    public static void clearTouchTimes(String dimensionId, UUID teamId) {
        if (dimensionId == null || teamId == null) {
            return;
        }

        Map<UUID, Long> dimensionTimes = teamTouchTimes.get(dimensionId);
        if (dimensionTimes == null) {
            return;
        }

        dimensionTimes.remove(teamId);

        if (dimensionTimes.isEmpty()) {
            teamTouchTimes.remove(dimensionId);
        }
    }

    /**
     * Checks if the integration is currently running.
     *
     * @return {@code true} if the periodic update task is active, {@code false} otherwise
     */
    public static boolean isRunning() {
        return running.get();
    }

    /**
     * Clears all touch time data for all teams in all dimensions.
     * Used during full reload operations.
     */
    public static void clearAllTouchTimes() {
        teamTouchTimes.clear();
        LOGGER.debug("Cleared all team touch times");
    }

    // ==================== Private Helper Methods ====================

    private static void performPeriodicUpdate() {
        try {
            if (server == null || server.isStopped()) {
                return;
            }

            for (ServerLevel level : server.getAllLevels()) {
                ClaimRenderer.updateDimensionClaims(level.dimension());
            }
        } catch (Exception e) {
            LOGGER.error("Error during scheduled claim update", e);
        }
    }

    private static void cancelUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel(false);
            updateTask = null;
        }
    }

    private static void clearAllCaches() {
        teamTouchTimes.clear();
        RegionGrouper.clearAll();
        ClaimRenderer.clearAll();
    }
}