package com.piglinmine.blueftbchunks.data;

import com.piglinmine.blueftbchunks.BlueChunks;
import com.piglinmine.blueftbchunks.bluemap.BlueMapMarkerService;
import com.piglinmine.blueftbchunks.ftbchunks.ClaimRenderer;
import com.piglinmine.blueftbchunks.ftbchunks.FTBChunksClaimProvider;
import com.piglinmine.blueftbchunks.ftbchunks.FTBChunksIntegration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages persistent storage of chunk claim data.
 *
 * <p>This class handles loading, saving, and synchronizing claim data between
 * the in-memory cache and a JSON file on disk. It provides:</p>
 * <ul>
 *   <li>Initial load on server start</li>
 *   <li>Periodic auto-save to prevent data loss</li>
 *   <li>Full synchronization with live FTB Chunks API</li>
 *   <li>Manual save/reload via commands</li>
 * </ul>
 */
public final class ChunkDataManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DATA_FILE = "claim_data.json";
    private static final int AUTO_SAVE_INTERVAL_MINUTES = 5;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final ChunkDataManager INSTANCE = new ChunkDataManager();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    private ChunkClaimData claimData;
    private MinecraftServer server;
    private ScheduledExecutorService autoSaveScheduler;
    private ScheduledFuture<?> autoSaveTask;

    private ChunkDataManager() {
        this.claimData = new ChunkClaimData();
    }

    /**
     * Returns the singleton instance.
     *
     * @return the chunk data manager instance
     */
    public static ChunkDataManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the data manager with the given server instance.
     * Loads persisted data and synchronizes with live FTB Chunks API.
     *
     * @param minecraftServer the Minecraft server
     */
    public void initialize(MinecraftServer minecraftServer) {
        if (initialized.getAndSet(true)) {
            LOGGER.warn("ChunkDataManager already initialized");
            return;
        }

        this.server = minecraftServer;
        LOGGER.info("Initializing ChunkDataManager...");

        loadFromFile();
        synchronizeWithFTBChunks();
        startAutoSave();

        LOGGER.info("ChunkDataManager initialized with {} chunks from {} teams",
                claimData.getTotalChunkCount(), claimData.getTotalTeamCount());
    }

    /**
     * Shuts down the data manager, saving any pending changes.
     */
    public void shutdown() {
        if (!initialized.getAndSet(false)) {
            return;
        }

        LOGGER.info("Shutting down ChunkDataManager...");

        stopAutoSave();

        if (dirty.get()) {
            saveToFile();
        }

        // Clear data to prevent memory leaks
        claimData.clear();
        dirty.set(false);

        server = null;
        LOGGER.info("ChunkDataManager shutdown complete");
    }

    /**
     * Returns the current in-memory claim data.
     *
     * @return the claim data
     */
    public ChunkClaimData getClaimData() {
        return claimData;
    }

    /**
     * Marks the data as modified, triggering a save on the next auto-save cycle.
     */
    public void markDirty() {
        dirty.set(true);
    }

    /**
     * Checks if the data manager is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Triggers BlueMap marker creation for all stored claims.
     * Should be called after BlueMap is fully initialized.
     * Uses a delayed execution to ensure FTB Chunks data is fully loaded.
     */
    public void triggerInitialMapRender() {
        if (server == null) {
            LOGGER.warn("Cannot trigger initial render: server not available");
            return;
        }

        // Schedule the initial render with a delay to ensure FTB Chunks is ready
        // FTB Chunks may not have loaded all claim data immediately
        CompletableFuture.runAsync(() -> {
            try {
                // Wait a bit for FTB Chunks to fully initialize
                Thread.sleep(3000);

                LOGGER.info("Triggering initial BlueMap marker creation...");

                // Execute on the server thread to ensure thread safety
                server.execute(() -> {
                    try {
                        int totalChunks = 0;
                        for (ServerLevel level : server.getAllLevels()) {
                            Map<UUID, List<dev.ftb.mods.ftbchunks.api.ClaimedChunk>> claims =
                                FTBChunksClaimProvider.getInstance().getClaimedChunksByTeam(level.dimension());
                            int dimChunks = claims.values().stream().mapToInt(List::size).sum();
                            totalChunks += dimChunks;

                            LOGGER.debug("Dimension {}: {} teams, {} chunks",
                                level.dimension().location(), claims.size(), dimChunks);

                            if (!claims.isEmpty()) {
                                ClaimRenderer.updateDimensionClaims(level.dimension());
                            }
                        }
                        LOGGER.info("Initial BlueMap marker creation complete: {} total chunks rendered", totalChunks);
                    } catch (Exception e) {
                        LOGGER.error("Error during initial BlueMap render", e);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Initial render was interrupted");
            }
        });
    }

    /**
     * Returns the server instance.
     *
     * @return the Minecraft server, or null if not initialized
     */
    public MinecraftServer getServer() {
        return server;
    }

    // ==================== File Operations ====================

    /**
     * Gets the path to the data file in the world folder.
     *
     * @return the data file path
     */
    private Path getDataFilePath() {
        if (server == null) {
            LOGGER.warn("Server not available, using fallback path");
            return FMLPaths.CONFIGDIR.get()
                    .resolve(BlueChunks.MOD_ID)
                    .resolve(DATA_FILE);
        }

        // Save in the world folder: <world>/ftbchunksbluemap/claim_data.json
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve(BlueChunks.MOD_ID)
                .resolve(DATA_FILE);
    }

    /**
     * Loads claim data from the JSON file.
     * Creates default data if the file doesn't exist.
     */
    public void loadFromFile() {
        Path dataFile = getDataFilePath();

        if (!Files.exists(dataFile)) {
            LOGGER.info("No existing data file found, starting fresh");
            claimData = new ChunkClaimData();
            return;
        }

        try {
            String json = Files.readString(dataFile, StandardCharsets.UTF_8);
            ChunkClaimData loaded = GSON.fromJson(json, ChunkClaimData.class);

            if (loaded != null) {
                claimData = loaded;
                LOGGER.info("Loaded {} chunks from {} teams from data file (saved: {})",
                        claimData.getTotalChunkCount(),
                        claimData.getTotalTeamCount(),
                        new Date(claimData.getLastSaved()));
            } else {
                LOGGER.warn("Data file was empty or invalid, starting fresh");
                claimData = new ChunkClaimData();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load data file: {}", e.getMessage());
            claimData = new ChunkClaimData();
        } catch (Exception e) {
            LOGGER.error("Error parsing data file", e);
            claimData = new ChunkClaimData();
        }
    }

    /**
     * Saves the current claim data to the JSON file.
     *
     * @return true if save was successful
     */
    public boolean saveToFile() {
        Path dataFile = getDataFilePath();

        try {
            Files.createDirectories(dataFile.getParent());

            claimData.setLastSaved(System.currentTimeMillis());
            String json = GSON.toJson(claimData);

            Files.writeString(dataFile, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            dirty.set(false);
            LOGGER.info("Saved {} chunks from {} teams to data file",
                    claimData.getTotalChunkCount(), claimData.getTotalTeamCount());
            return true;

        } catch (IOException e) {
            LOGGER.error("Failed to save data file: {}", e.getMessage());
            return false;
        }
    }

    // ==================== Synchronization ====================

    /**
     * Performs a full synchronization with the live FTB Chunks API.
     * Updates the in-memory cache with current claim data.
     */
    public void synchronizeWithFTBChunks() {
        if (server == null) {
            LOGGER.warn("Cannot synchronize: server not available");
            return;
        }

        LOGGER.info("Starting synchronization with FTB Chunks API...");

        FTBChunksClaimProvider provider = FTBChunksClaimProvider.getInstance();
        int changes = 0;

        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> dimension = level.dimension();
            String dimensionId = dimension.location().toString();

            Map<UUID, List<ClaimedChunk>> claimsByTeam = provider.getClaimedChunksByTeam(dimension);

            ChunkClaimData.DimensionClaims dimClaims = claimData.getOrCreateDimension(dimensionId);

            for (Map.Entry<UUID, List<ClaimedChunk>> entry : claimsByTeam.entrySet()) {
                UUID teamId = entry.getKey();
                String teamIdStr = teamId.toString();
                List<ClaimedChunk> chunks = entry.getValue();

                Optional<Team> teamOpt = provider.getTeamById(teamId);
                if (teamOpt.isEmpty()) {
                    continue;
                }

                Team team = teamOpt.get();
                ChunkClaimData.TeamClaimData teamData = dimClaims.getOrCreateTeam(teamIdStr);

                String newName = provider.getTeamDisplayName(team);
                int newColor = provider.getTeamColor(team);

                if (!teamData.getTeamName().equals(newName)) {
                    teamData.setTeamName(newName);
                    changes++;
                }
                if (teamData.getTeamColor() != newColor) {
                    teamData.setTeamColor(newColor);
                    changes++;
                }

                Set<String> currentChunks = new HashSet<>(teamData.getChunks());
                Set<String> liveChunks = new HashSet<>();

                for (ClaimedChunk chunk : chunks) {
                    int x = chunk.getPos().x();
                    int z = chunk.getPos().z();
                    String chunkKey = ChunkClaimData.TeamClaimData.formatChunkKey(x, z);
                    liveChunks.add(chunkKey);

                    if (!currentChunks.contains(chunkKey)) {
                        teamData.addChunk(x, z);
                        changes++;
                    }
                }

                for (String oldChunk : currentChunks) {
                    if (!liveChunks.contains(oldChunk)) {
                        teamData.getChunks().remove(oldChunk);
                        changes++;
                    }
                }

                boolean hasForceLoaded = chunks.stream().anyMatch(ClaimedChunk::isForceLoaded);
                if (teamData.isHasForceLoaded() != hasForceLoaded) {
                    teamData.setHasForceLoaded(hasForceLoaded);
                    changes++;
                }
            }

            Set<String> teamsToRemove = new HashSet<>();
            for (String teamIdStr : dimClaims.getTeams().keySet()) {
                if (!claimsByTeam.containsKey(UUID.fromString(teamIdStr))) {
                    teamsToRemove.add(teamIdStr);
                }
            }
            for (String teamIdStr : teamsToRemove) {
                dimClaims.removeTeam(teamIdStr);
                changes++;
                LOGGER.debug("Removed team {} from dimension {} (no longer has claims)", teamIdStr, dimensionId);
            }
        }

        if (changes > 0) {
            markDirty();
            LOGGER.info("Synchronization complete: {} changes detected", changes);
        } else {
            LOGGER.info("Synchronization complete: no changes");
        }

    }

    /**
     * Forces a complete re-initialization: clears data, reloads from file, and re-syncs.
     */
    public void forceReinitialize() {
        LOGGER.info("Forcing complete re-initialization...");

        claimData.clear();
        loadFromFile();
        synchronizeWithFTBChunks();
        saveToFile();

        LOGGER.info("Re-initialization complete");
    }

    /**
     * Performs a full flush of all memory caches and reloads everything from scratch.
     * This is the most thorough reload option, clearing all internal state.
     *
     * @param server the Minecraft server instance
     */
    public void fullFlushAndReload(MinecraftServer server) {
        LOGGER.info("Performing full memory flush and reload...");

        // Clear all data manager state
        claimData.clear();
        dirty.set(false);

        // Clear ClaimRenderer caches
        ClaimRenderer.clearAll();

        // Clear RegionGrouper caches
        com.piglinmine.blueftbchunks.ftbchunks.region.RegionGrouper.clearAll();

        // Clear FTBChunksIntegration touch times
        FTBChunksIntegration.clearAllTouchTimes();

        // Remove all existing BlueMap markers
        removeAllBlueMapMarkers(server);

        // Reload from file
        loadFromFile();

        // Sync with FTB Chunks
        synchronizeWithFTBChunks();

        // Save to file
        saveToFile();

        // Trigger BlueMap marker recreation
        triggerFullMapRender(server);

        LOGGER.info("Full flush and reload complete");
    }

    /**
     * Removes all BlueMap markers for all dimensions.
     */
    private void removeAllBlueMapMarkers(MinecraftServer server) {
        if (server == null) {
            return;
        }

        LOGGER.debug("Removing all BlueMap markers...");
        BlueMapMarkerService markerService = BlueMapMarkerService.getInstance();

        for (ServerLevel level : server.getAllLevels()) {
            markerService.removeAllMarkers(level.dimension());
        }
    }

    /**
     * Triggers a full map render for all dimensions immediately (no delay).
     */
    private void triggerFullMapRender(MinecraftServer server) {
        if (server == null) {
            return;
        }

        LOGGER.info("Triggering full BlueMap marker recreation...");

        int totalChunks = 0;
        for (ServerLevel level : server.getAllLevels()) {
            Map<UUID, List<ClaimedChunk>> claims =
                    FTBChunksClaimProvider.getInstance().getClaimedChunksByTeam(level.dimension());
            int dimChunks = claims.values().stream().mapToInt(List::size).sum();
            totalChunks += dimChunks;

            if (!claims.isEmpty()) {
                ClaimRenderer.updateDimensionClaims(level.dimension());
            }
        }

        LOGGER.info("Full map render complete: {} total chunks", totalChunks);
    }

    /**
     * Reloads data from file and synchronizes with FTB Chunks.
     */
    public void reloadAndSync() {
        LOGGER.info("Reloading data and synchronizing...");

        loadFromFile();
        synchronizeWithFTBChunks();
        saveToFile();

        LOGGER.info("Reload complete");
    }

    // ==================== Auto-Save ====================

    /**
     * Starts the auto-save scheduler.
     */
    private void startAutoSave() {
        if (autoSaveScheduler != null) {
            return;
        }

        autoSaveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "FTBChunksBlueMap-AutoSave");
            thread.setDaemon(true);
            return thread;
        });

        autoSaveTask = autoSaveScheduler.scheduleAtFixedRate(
                this::performAutoSave,
                AUTO_SAVE_INTERVAL_MINUTES,
                AUTO_SAVE_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );

        LOGGER.debug("Auto-save scheduler started (interval: {} minutes)", AUTO_SAVE_INTERVAL_MINUTES);
    }

    /**
     * Stops the auto-save scheduler.
     */
    private void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel(false);
            autoSaveTask = null;
        }

        if (autoSaveScheduler != null) {
            autoSaveScheduler.shutdown();
            try {
                if (!autoSaveScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    autoSaveScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                autoSaveScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            autoSaveScheduler = null;
        }

        LOGGER.debug("Auto-save scheduler stopped");
    }

    /**
     * Performs an auto-save if data has been modified.
     */
    private void performAutoSave() {
        if (!dirty.get()) {
            LOGGER.debug("Auto-save skipped: no changes");
            return;
        }

        try {
            synchronizeWithFTBChunks();
            saveToFile();
            LOGGER.debug("Auto-save completed");
        } catch (Exception e) {
            LOGGER.error("Auto-save failed", e);
        }
    }

    // ==================== Data Access ====================

    /**
     * Updates claim data for a specific chunk.
     *
     * @param dimensionId the dimension ID
     * @param teamId      the team UUID
     * @param teamName    the team display name
     * @param teamColor   the team color
     * @param chunkX      the chunk X coordinate
     * @param chunkZ      the chunk Z coordinate
     * @param claimed     true if claimed, false if unclaimed
     */
    public void updateChunkClaim(String dimensionId, UUID teamId, String teamName, int teamColor,
                                  int chunkX, int chunkZ, boolean claimed) {
        ChunkClaimData.DimensionClaims dimClaims = claimData.getOrCreateDimension(dimensionId);
        ChunkClaimData.TeamClaimData teamData = dimClaims.getOrCreateTeam(teamId.toString());

        teamData.setTeamName(teamName);
        teamData.setTeamColor(teamColor);

        if (claimed) {
            teamData.addChunk(chunkX, chunkZ);
        } else {
            teamData.removeChunk(chunkX, chunkZ);
            if (teamData.getChunks().isEmpty()) {
                dimClaims.removeTeam(teamId.toString());
            }
        }

        markDirty();
    }

    /**
     * Gets the team data for a specific team in a dimension.
     *
     * @param dimensionId the dimension ID
     * @param teamId      the team UUID
     * @return optional team data
     */
    public Optional<ChunkClaimData.TeamClaimData> getTeamData(String dimensionId, UUID teamId) {
        ChunkClaimData.DimensionClaims dimClaims = claimData.getDimensions().get(dimensionId);
        if (dimClaims == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(dimClaims.getTeams().get(teamId.toString()));
    }

    /**
     * Gets all teams with claims in a dimension.
     *
     * @param dimensionId the dimension ID
     * @return map of team ID to team data
     */
    public Map<String, ChunkClaimData.TeamClaimData> getTeamsInDimension(String dimensionId) {
        ChunkClaimData.DimensionClaims dimClaims = claimData.getDimensions().get(dimensionId);
        if (dimClaims == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(dimClaims.getTeams());
    }
}

