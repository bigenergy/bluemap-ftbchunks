package com.piglinmine.blueftbchunks.ftbchunks;

import com.piglinmine.blueftbchunks.data.ChunkDataManager;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches claimed chunk data and triggers BlueMap marker updates when claims change.
 *
 * <p>This class provides a caching layer between FTB Chunks events and the BlueMap
 * rendering system. It maintains an internal representation of claimed chunks
 * organized by dimension and team, along with cached team metadata.</p>
 *
 * <p>When claims are added or removed, the cache automatically triggers a
 * BlueMap update for the affected dimension.</p>
 */
public class ClaimCache {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<ResourceLocation, Map<UUID, Set<ChunkDimPos>>> claimsByDimension;
    private final Map<UUID, TeamData> teamDataCache;

    /**
     * Creates a new empty claim cache.
     */
    public ClaimCache() {
        this.claimsByDimension = new ConcurrentHashMap<>();
        this.teamDataCache = new ConcurrentHashMap<>();
    }

    /**
     * Adds a claimed chunk to the cache and triggers a BlueMap update.
     *
     * @param chunk the claimed chunk to add; ignored if null or invalid
     */
    public void addClaim(ClaimedChunk chunk) {
        if (isInvalidChunk(chunk)) {
            LOGGER.warn("Attempted to add null or invalid chunk claim");
            return;
        }

        try {
            ChunkDimPos position = chunk.getPos();
            ResourceLocation dimensionId = position.dimension().location();
            Team team = chunk.getTeamData().getTeam();
            UUID teamId = team.getTeamId();

            LOGGER.debug("Adding claim at {} in {}", position, dimensionId);

            addChunkToCache(dimensionId, teamId, position);
            cacheTeamData(chunk);

            persistClaimChange(dimensionId.toString(), team, position.x(), position.z(), true);

            scheduleMapUpdate(position.dimension());

        } catch (Exception e) {
            LOGGER.error("Error adding claim to cache", e);
        }
    }

    /**
     * Removes a claimed chunk from the cache and triggers a BlueMap update.
     *
     * @param chunk the claimed chunk to remove; ignored if null or invalid
     */
    public void removeClaim(ClaimedChunk chunk) {
        if (isInvalidChunk(chunk)) {
            LOGGER.warn("Attempted to remove null or invalid chunk claim");
            return;
        }

        try {
            ChunkDimPos position = chunk.getPos();
            ResourceLocation dimensionId = position.dimension().location();
            Team team = chunk.getTeamData().getTeam();
            UUID teamId = team.getTeamId();

            LOGGER.debug("Removing claim at {} in {}", position, dimensionId);

            removeChunkFromCache(dimensionId, teamId, position);

            persistClaimChange(dimensionId.toString(), team, position.x(), position.z(), false);

            scheduleMapUpdate(position.dimension());

        } catch (Exception e) {
            LOGGER.error("Error removing claim from cache", e);
        }
    }

    /**
     * Clears all cached data including claims and team metadata.
     */
    public void clear() {
        claimsByDimension.clear();
        teamDataCache.clear();
        LOGGER.debug("Cleared claim cache");
    }

    /**
     * Returns the number of dimensions that have cached claims.
     *
     * @return the count of dimensions with claims
     */
    public int getDimensionCount() {
        return claimsByDimension.size();
    }

    /**
     * Returns the number of teams that have cached data.
     *
     * @return the count of cached teams
     */
    public int getTeamCount() {
        return teamDataCache.size();
    }

    /**
     * Retrieves cached data for a team.
     *
     * @param teamId the UUID of the team
     * @return an Optional containing the team data if cached, or empty if not found
     */
    public Optional<TeamData> getTeamData(UUID teamId) {
        return Optional.ofNullable(teamDataCache.get(teamId));
    }

    // ==================== Private Helper Methods ====================

    private boolean isInvalidChunk(ClaimedChunk chunk) {
        return chunk == null || chunk.getPos() == null;
    }

    private void addChunkToCache(ResourceLocation dimensionId, UUID teamId, ChunkDimPos position) {
        claimsByDimension
                .computeIfAbsent(dimensionId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(teamId, k -> ConcurrentHashMap.newKeySet())
                .add(position);
    }

    private void removeChunkFromCache(ResourceLocation dimensionId, UUID teamId, ChunkDimPos position) {
        Map<UUID, Set<ChunkDimPos>> teamClaims = claimsByDimension.get(dimensionId);
        if (teamClaims == null) {
            return;
        }

        Set<ChunkDimPos> claims = teamClaims.get(teamId);
        if (claims != null) {
            claims.remove(position);
            cleanupEmptyCollections(dimensionId, teamId, teamClaims, claims);
        }
    }

    private void cleanupEmptyCollections(ResourceLocation dimensionId, UUID teamId,
                                          Map<UUID, Set<ChunkDimPos>> teamClaims, Set<ChunkDimPos> claims) {
        if (claims.isEmpty()) {
            teamClaims.remove(teamId);
        }
        if (teamClaims.isEmpty()) {
            claimsByDimension.remove(dimensionId);
        }
    }

    private void cacheTeamData(ClaimedChunk chunk) {
        try {
            Team team = chunk.getTeamData().getTeam();
            String teamName = extractCleanTeamName(team.getShortName());
            int teamColor = FTBChunksClaimProvider.getInstance().getTeamColor(team);

            teamDataCache.put(team.getTeamId(), new TeamData(teamName, teamColor));
        } catch (Exception e) {
            LOGGER.warn("Failed to cache team data", e);
        }
    }

    private String extractCleanTeamName(String shortName) {
        if (shortName == null || shortName.isBlank()) {
            return "Unknown";
        }

        int hashIndex = shortName.indexOf('#');
        return hashIndex > 0 ? shortName.substring(0, hashIndex) : shortName;
    }

    private void scheduleMapUpdate(ResourceKey<Level> dimension) {
        try {
            ClaimRenderer.updateDimensionClaims(dimension);
        } catch (Exception e) {
            LOGGER.debug("Deferred update for dimension {}: {}", dimension.location(), e.getMessage());
        }
    }

    /**
     * Persists a claim change to the ChunkDataManager.
     */
    private void persistClaimChange(String dimensionId, Team team, int chunkX, int chunkZ, boolean claimed) {
        try {
            ChunkDataManager manager = ChunkDataManager.getInstance();
            if (manager.isInitialized()) {
                String teamName = extractCleanTeamName(team.getShortName());
                int teamColor = FTBChunksClaimProvider.getInstance().getTeamColor(team);
                manager.updateChunkClaim(dimensionId, team.getTeamId(), teamName, teamColor, chunkX, chunkZ, claimed);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not persist claim change: {}", e.getMessage());
        }
    }

    // ==================== Data Classes ====================

    /**
     * Immutable record containing cached team display information.
     *
     * @param teamName  the team's display name
     * @param teamColor the team's color as an RGB integer
     */
    public record TeamData(String teamName, int teamColor) {
        /**
         * Creates a new TeamData record.
         *
         * @param teamName  the team name; must not be null
         * @param teamColor the team color as RGB
         * @throws NullPointerException if teamName is null
         */
        public TeamData {
            Objects.requireNonNull(teamName, "Team name cannot be null");
        }
    }
}

