package com.piglinmine.blueftbchunks.bluemap;

import com.piglinmine.blueftbchunks.config.ModConfig;
import com.piglinmine.blueftbchunks.data.ChunkDataManager;
import com.piglinmine.blueftbchunks.ftbchunks.FTBChunksIntegration;
import com.mojang.logging.LogUtils;
import de.bluecolored.bluemap.api.BlueMapAPI;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Manages the lifecycle of BlueMap integrations and plugins.
 *
 * <p>This class provides a centralized registry for plugins that need to hook into
 * BlueMap's initialization and shutdown events. It ensures plugins are started
 * when the server starts and properly cleaned up when it stops.</p>
 *
 * <p>The FTB Chunks integration is registered by default in the static initializer.</p>
 */
public final class BlueMapManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<BlueMapPlugin> plugins = new ArrayList<>();
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean blueMapReady = new AtomicBoolean(false);

    // Store callback references for proper cleanup
    private static Consumer<BlueMapAPI> onEnableCallback;
    private static Consumer<BlueMapAPI> onDisableCallback;

    static {
        registerPlugin(new BlueMapPlugin(
                "FTBChunks",
                FTBChunksIntegration::init,
                FTBChunksIntegration::stop
        ));
    }

    private BlueMapManager() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Registers a plugin to be initialized alongside BlueMap.
     *
     * <p>Plugins must be registered before {@link #init(MinecraftServer)} is called,
     * typically during mod construction.</p>
     *
     * @param plugin the plugin to register
     * @throws IllegalStateException if called after BlueMap has been initialized
     */
    public static void registerPlugin(BlueMapPlugin plugin) {
        if (initialized.get()) {
            throw new IllegalStateException("Cannot register plugins after BlueMap initialization");
        }

        plugins.add(plugin);
        LOGGER.debug("Registered BlueMap plugin: {}", plugin.name());
    }

    /**
     * Initializes all registered plugins and sets up BlueMap API callback.
     *
     * <p>This method registers a callback with BlueMap's API to trigger initial
     * marker creation when BlueMap becomes available.</p>
     *
     * @param server the Minecraft server instance
     */
    public static void init(MinecraftServer server) {
        if (!initialized.compareAndSet(false, true)) {
            LOGGER.warn("BlueMap already initialized, skipping");
            return;
        }

        if (ModConfig.enableLogs) LOGGER.info("Initializing {} BlueMap plugin(s)", plugins.size());
        InitializationResult result = initializeAllPlugins(server);
        if (ModConfig.enableLogs) LOGGER.info("BlueMap initialization complete ({} successful, {} failed)",
                result.successful, result.failed);

        // Register callback for when BlueMap API becomes available
        onEnableCallback = api -> {
            if (ModConfig.enableLogs) LOGGER.info("BlueMap API is now available, triggering initial marker creation...");
            blueMapReady.set(true);

            // Trigger initial marker creation for all stored claims
            ChunkDataManager dataManager = ChunkDataManager.getInstance();
            if (dataManager.isInitialized()) {
                dataManager.triggerInitialMapRender();
            }
        };
        BlueMapAPI.onEnable(onEnableCallback);

        onDisableCallback = api -> {
            if (ModConfig.enableLogs) LOGGER.info("BlueMap API is shutting down");
            blueMapReady.set(false);
        };
        BlueMapAPI.onDisable(onDisableCallback);

        // Check if BlueMap is already available (in case it loaded before us)
        BlueMapAPI.getInstance().ifPresent(api -> {
            if (blueMapReady.compareAndSet(false, true)) {
                if (ModConfig.enableLogs) LOGGER.info("BlueMap API already available, triggering initial marker creation...");
                ChunkDataManager dataManager = ChunkDataManager.getInstance();
                if (dataManager.isInitialized()) {
                    dataManager.triggerInitialMapRender();
                }
            }
        });
    }

    /**
     * Shuts down all registered plugins.
     *
     * <p>This method should be called during server shutdown. It iterates through
     * all registered plugins and invokes their shutdown handlers.</p>
     */
    public static void shutdown() {
        if (!initialized.get()) {
            LOGGER.debug("BlueMap not initialized, skipping shutdown");
            return;
        }

        if (ModConfig.enableLogs) LOGGER.info("Shutting down BlueMap plugins");

        // Unregister BlueMap callbacks to prevent memory leaks
        if (onEnableCallback != null) {
            BlueMapAPI.unregisterListener(onEnableCallback);
            onEnableCallback = null;
        }
        if (onDisableCallback != null) {
            BlueMapAPI.unregisterListener(onDisableCallback);
            onDisableCallback = null;
        }

        shutdownAllPlugins();
        initialized.set(false);
        blueMapReady.set(false);
        if (ModConfig.enableLogs) LOGGER.info("BlueMap shutdown complete");
    }

    /**
     * Checks if BlueMap has been initialized.
     *
     * @return {@code true} if initialized, {@code false} otherwise
     */
    public static boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Checks if BlueMap API is ready and available.
     *
     * @return {@code true} if BlueMap API is available, {@code false} otherwise
     */
    public static boolean isBlueMapReady() {
        return blueMapReady.get();
    }

    // ==================== Private Helper Methods ====================

    private static InitializationResult initializeAllPlugins(MinecraftServer server) {
        int successful = 0;
        int failed = 0;

        for (BlueMapPlugin plugin : plugins) {
            try {
                plugin.initializer().accept(server);
                successful++;
                LOGGER.debug("Successfully initialized plugin: {}", plugin.name());
            } catch (Exception e) {
                failed++;
                LOGGER.error("Failed to initialize BlueMap plugin: {}", plugin.name(), e);
            }
        }

        return new InitializationResult(successful, failed);
    }

    private static void shutdownAllPlugins() {
        for (BlueMapPlugin plugin : plugins) {
            if (plugin.shutdown() != null) {
                try {
                    plugin.shutdown().run();
                    LOGGER.debug("Successfully shut down plugin: {}", plugin.name());
                } catch (Exception e) {
                    LOGGER.error("Error shutting down BlueMap plugin: {}", plugin.name(), e);
                }
            }
        }
    }

    // ==================== Inner Classes ====================

    private record InitializationResult(int successful, int failed) {}

    /**
     * Represents a plugin that integrates with BlueMap.
     *
     * @param name        the plugin name used for logging
     * @param initializer the function to call during initialization
     * @param shutdown    the function to call during shutdown; may be null
     */
    public record BlueMapPlugin(
            String name,
            Consumer<MinecraftServer> initializer,
            Runnable shutdown
    ) {
        /**
         * Creates a plugin with no shutdown handler.
         *
         * @param name        the plugin name
         * @param initializer the initialization function
         */
        public BlueMapPlugin(String name, Consumer<MinecraftServer> initializer) {
            this(name, initializer, null);
        }
    }
}