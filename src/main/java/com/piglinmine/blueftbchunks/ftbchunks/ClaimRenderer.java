package com.piglinmine.blueftbchunks.ftbchunks;

import com.piglinmine.blueftbchunks.api.ChunkClaimProvider;
import com.piglinmine.blueftbchunks.bluemap.BlueMapMarkerService;
import com.piglinmine.blueftbchunks.ftbchunks.region.ClaimRegion;
import com.piglinmine.blueftbchunks.ftbchunks.region.RegionGrouper;
import com.piglinmine.blueftbchunks.util.ShapeBuilder;
import com.flowpowered.math.vector.Vector2i;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the rendering of FTB Chunks claims on BlueMap.
 *
 * <p>This class orchestrates the synchronization between claim data from FTB Chunks
 * and markers displayed on BlueMap. It handles:</p>
 * <ul>
 *   <li>Periodic updates of all claims in a dimension</li>
 *   <li>Individual team claim updates</li>
 *   <li>Efficient diff-based marker management</li>
 *   <li>Overlap resolution between teams</li>
 * </ul>
 *
 * <p>The renderer uses a caching strategy to minimize BlueMap API calls by computing
 * differences between the current and previous state of markers.</p>
 */
public final class ClaimRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, Map<UUID, Set<String>>> activeMarkersByDimension = new ConcurrentHashMap<>();
    private static final Map<String, Map<UUID, Map<String, String>>> holeSignaturesByDimension = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> cachedTeamColors = new ConcurrentHashMap<>();

    private static final ChunkClaimProvider claimProvider = FTBChunksClaimProvider.getInstance();
    private static final BlueMapMarkerService markerService = BlueMapMarkerService.getInstance();

    private ClaimRenderer() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Updates all claim markers for every team in the specified dimension.
     *
     * <p>This is the primary entry point for periodic updates. It fetches all claims,
     * resolves any overlaps, and updates the BlueMap markers accordingly.</p>
     *
     * <p>This method also detects:</p>
     * <ul>
     *   <li>Teams that no longer exist (deleted) and removes their markers</li>
     *   <li>Teams whose color has changed and rebuilds their markers</li>
     * </ul>
     *
     * @param dimension the dimension to update claims for
     */
    public static void updateDimensionClaims(ResourceKey<Level> dimension) {
        String dimensionId = getDimensionId(dimension);

        Map<UUID, List<ClaimedChunk>> allClaims = claimProvider.getClaimedChunksByTeam(dimension);

        int totalChunks = allClaims.values().stream().mapToInt(List::size).sum();
        LOGGER.info("updateDimensionClaims for {}: found {} teams with {} total chunks",
                dimensionId, allClaims.size(), totalChunks);

        if (allClaims.isEmpty()) {
            LOGGER.debug("No claims found in dimension {}", dimensionId);
            return;
        }

        removeMarkersForDeletedTeams(dimension, dimensionId, allClaims.keySet());

        Map<UUID, List<ClaimedChunk>> resolvedClaims = resolveOverlappingClaims(dimensionId, allClaims);

        resolvedClaims.forEach((teamId, claims) -> updateTeamClaims(dimension, teamId, claims));
        removeMarkersForAbsentTeams(dimension, dimensionId, resolvedClaims.keySet());
    }

    /**
     * Updates claims for a specific team by fetching their current claim data.
     *
     * @param dimension the dimension containing the claims
     * @param teamId    the UUID of the team to update
     */
    public static void updateTeamClaims(ResourceKey<Level> dimension, UUID teamId) {
        List<ClaimedChunk> claims = claimProvider.getClaimedChunksForTeam(dimension, teamId);
        updateTeamClaims(dimension, teamId, claims);
    }

    /**
     * Updates claims for a specific team with the provided claim data.
     *
     * <p>This method computes the differences between existing and new markers,
     * then applies only the necessary changes to BlueMap. It also detects team
     * color changes and forces a full rebuild when the color has changed.</p>
     *
     * @param dimension the dimension containing the claims
     * @param teamId    the UUID of the team
     * @param claims    the list of claimed chunks for this team
     */
    public static void updateTeamClaims(ResourceKey<Level> dimension, UUID teamId, List<ClaimedChunk> claims) {
        String dimensionId = getDimensionId(dimension);
        LOGGER.debug("Updating team {} with {} claim(s) in {}", teamId, claims.size(), dimensionId);

        FTBChunksIntegration.updateTeamTouchTime(dimensionId, teamId);

        if (claims.isEmpty()) {
            removeAllTeamMarkers(dimension, teamId);
            return;
        }

        List<ClaimRegion> regions = RegionGrouper.groupChunksIntoRegions(claims, teamId, dimension);

        boolean colorChanged = hasTeamColorChanged(teamId, regions);
        MarkerDiff diff = computeMarkerDifferences(dimensionId, teamId, regions);

        if (colorChanged) {
            diff = forceFullRebuild(diff);
            LOGGER.debug("Team {} color changed, forcing full marker rebuild", teamId);
        }

        boolean success = applyMarkerChanges(dimension, diff, regions);

        // Only update caches if markers were successfully created/updated
        if (success) {
            updateMarkerCaches(dimensionId, teamId, diff, regions);
        } else {
            LOGGER.debug("Skipping cache update - BlueMap not available for {}", dimensionId);
        }
    }

    /**
     * Removes all markers for a team and cleans up associated caches.
     *
     * @param dimension the dimension to remove markers from
     * @param teamId    the UUID of the team
     */
    public static void removeAllTeamMarkers(ResourceKey<Level> dimension, UUID teamId) {
        String dimensionId = getDimensionId(dimension);
        Map<UUID, Set<String>> dimensionMarkers = activeMarkersByDimension.get(dimensionId);

        if (dimensionMarkers == null) {
            return;
        }

        Set<String> markerIds = dimensionMarkers.remove(teamId);
        if (markerIds != null && !markerIds.isEmpty()) {
            markerService.removeMarkers(dimension, markerIds);
            LOGGER.debug("Removed {} markers for team {} in {}", markerIds.size(), teamId, dimensionId);
        }

        clearTeamCaches(dimensionId, teamId);
    }

    // ==================== Marker Diff Computation ====================

    private static MarkerDiff computeMarkerDifferences(String dimensionId, UUID teamId, List<ClaimRegion> newRegions) {
        Set<String> existingIds = getActiveMarkerIds(dimensionId, teamId);
        Map<String, String> existingSignatures = getHoleSignatures(dimensionId, teamId);

        Set<String> newIds = extractMarkerIds(newRegions);

        Set<String> toRemove = computeRemovals(existingIds, newIds);
        Set<String> toAdd = computeAdditions(existingIds, newIds);
        Set<String> toUpdate = computeUpdates(existingIds, newIds);
        Set<String> toRebuild = computeRebuilds(toUpdate, newRegions, existingSignatures);

        toUpdate.removeAll(toRebuild);

        return new MarkerDiff(toAdd, toUpdate, toRebuild, toRemove);
    }

    private static Set<String> extractMarkerIds(List<ClaimRegion> regions) {
        return regions.stream()
                .map(ClaimRegion::getMarkerId)
                .collect(Collectors.toSet());
    }

    private static Set<String> computeRemovals(Set<String> existing, Set<String> current) {
        Set<String> removals = new HashSet<>(existing);
        removals.removeAll(current);
        return removals;
    }

    private static Set<String> computeAdditions(Set<String> existing, Set<String> current) {
        Set<String> additions = new HashSet<>(current);
        additions.removeAll(existing);
        return additions;
    }

    private static Set<String> computeUpdates(Set<String> existing, Set<String> current) {
        Set<String> updates = new HashSet<>(current);
        updates.retainAll(existing);
        return updates;
    }

    private static Set<String> computeRebuilds(Set<String> candidates, List<ClaimRegion> regions,
                                                Map<String, String> existingSignatures) {
        Set<String> rebuilds = new HashSet<>();
        for (String markerId : candidates) {
            String newSignature = computeHoleSignatureForRegion(regions, markerId);
            if (!Objects.equals(existingSignatures.get(markerId), newSignature)) {
                rebuilds.add(markerId);
            }
        }
        return rebuilds;
    }

    // ==================== Marker Operations ====================

    /**
     * Applies marker changes to BlueMap.
     *
     * @return {@code true} if at least one marker operation succeeded, {@code false} if BlueMap wasn't available
     */
    private static boolean applyMarkerChanges(ResourceKey<Level> dimension, MarkerDiff diff, List<ClaimRegion> regions) {
        Map<String, ClaimRegion> regionsById = regions.stream()
                .collect(Collectors.toMap(ClaimRegion::getMarkerId, region -> region));

        boolean anySuccess = false;

        if (!diff.toRemove.isEmpty()) {
            markerService.removeMarkers(dimension, diff.toRemove);
            anySuccess = true;
        }

        for (String markerId : diff.toAdd) {
            if (markerService.createMarker(dimension, regionsById.get(markerId))) {
                anySuccess = true;
            }
        }

        if (!diff.toUpdate.isEmpty()) {
            markerService.updateMarkersInPlace(dimension, regionsById, diff.toUpdate);
            anySuccess = true;
        }

        if (!diff.toRebuild.isEmpty()) {
            markerService.removeMarkers(dimension, diff.toRebuild);
            for (String markerId : diff.toRebuild) {
                if (markerService.createMarker(dimension, regionsById.get(markerId))) {
                    anySuccess = true;
                }
            }
        }

        // If there were no changes to make but we have regions, check if BlueMap is available
        if (!anySuccess && !regions.isEmpty()) {
            // Try to create the first region to check availability
            return markerService.isAvailable();
        }

        return anySuccess;
    }

    // ==================== Cache Management ====================

    private static void updateMarkerCaches(String dimensionId, UUID teamId, MarkerDiff diff, List<ClaimRegion> regions) {
        Set<String> newIds = extractMarkerIds(regions);
        Map<String, String> newSignatures = regions.stream()
                .collect(Collectors.toMap(ClaimRegion::getMarkerId, ClaimRenderer::computeHoleSignature));

        activeMarkersByDimension
                .computeIfAbsent(dimensionId, k -> new ConcurrentHashMap<>())
                .put(teamId, newIds);

        holeSignaturesByDimension
                .computeIfAbsent(dimensionId, k -> new ConcurrentHashMap<>())
                .put(teamId, newSignatures);

        LOGGER.debug("Updated team {} in {}: +{} ~{} ^{} -{}",
                teamId, dimensionId,
                diff.toAdd.size(), diff.toUpdate.size(),
                diff.toRebuild.size(), diff.toRemove.size());
    }

    private static void clearTeamCaches(String dimensionId, UUID teamId) {
        Map<UUID, Map<String, String>> dimensionSignatures = holeSignaturesByDimension.get(dimensionId);
        if (dimensionSignatures != null) {
            dimensionSignatures.remove(teamId);
        }

        FTBChunksIntegration.clearTouchTimes(dimensionId, teamId);
        RegionGrouper.clearTeam(teamId);
        cachedTeamColors.remove(teamId);
    }

    private static Set<String> getActiveMarkerIds(String dimensionId, UUID teamId) {
        return activeMarkersByDimension
                .getOrDefault(dimensionId, Collections.emptyMap())
                .getOrDefault(teamId, Collections.emptySet());
    }

    private static Map<String, String> getHoleSignatures(String dimensionId, UUID teamId) {
        return holeSignaturesByDimension
                .getOrDefault(dimensionId, Collections.emptyMap())
                .getOrDefault(teamId, Collections.emptyMap());
    }

    // ==================== Overlap Resolution ====================

    private static Map<UUID, List<ClaimedChunk>> resolveOverlappingClaims(String dimensionId,
                                                                           Map<UUID, List<ClaimedChunk>> allClaims) {
        Map<UUID, Long> touchTimes = FTBChunksIntegration.getTouchTimes(dimensionId);

        List<Map.Entry<UUID, List<ClaimedChunk>>> sortedByTouchTime = allClaims.entrySet().stream()
                .sorted(Comparator.comparingLong(
                        (Map.Entry<UUID, List<ClaimedChunk>> entry) -> touchTimes.getOrDefault(entry.getKey(), 0L)
                ).reversed())
                .toList();

        Set<ChunkKey> occupiedChunks = new HashSet<>();
        Map<UUID, List<ClaimedChunk>> resolvedClaims = new LinkedHashMap<>();

        for (Map.Entry<UUID, List<ClaimedChunk>> entry : sortedByTouchTime) {
            List<ClaimedChunk> nonOverlappingClaims = entry.getValue().stream()
                    .filter(chunk -> occupiedChunks.add(ChunkKey.from(chunk.getPos())))
                    .toList();

            if (!nonOverlappingClaims.isEmpty()) {
                resolvedClaims.put(entry.getKey(), nonOverlappingClaims);
            }
        }

        return resolvedClaims;
    }

    // ==================== Hole Signature Computation ====================

    private static String computeHoleSignature(ClaimRegion region) {
        Vector2i[] chunks = region.getChunkCoordinates();
        ShapeBuilder shapeBuilder = ShapeBuilder.createSingleFromChunks(chunks);

        return shapeBuilder.getHoles().stream()
                .flatMap(hole -> Arrays.stream(hole.getPoints()))
                .map(point -> formatPointCoordinate(point.getX()) + "," + formatPointCoordinate(point.getY()))
                .sorted()
                .collect(Collectors.joining(";"));
    }

    private static String computeHoleSignatureForRegion(List<ClaimRegion> regions, String markerId) {
        return regions.stream()
                .filter(region -> region.getMarkerId().equals(markerId))
                .findFirst()
                .map(ClaimRenderer::computeHoleSignature)
                .orElse("");
    }

    private static int formatPointCoordinate(double value) {
        return (int) Math.round(value);
    }

    // ==================== Utility Methods ====================

    private static void removeMarkersForAbsentTeams(ResourceKey<Level> dimension, String dimensionId,
                                                     Set<UUID> presentTeams) {
        Map<UUID, Set<String>> dimensionMarkers = activeMarkersByDimension
                .computeIfAbsent(dimensionId, k -> new ConcurrentHashMap<>());

        dimensionMarkers.keySet().stream()
                .filter(teamId -> !presentTeams.contains(teamId))
                .toList()
                .forEach(teamId -> removeAllTeamMarkers(dimension, teamId));
    }

    /**
     * Removes markers for teams that no longer exist in the FTB Teams system.
     * This handles the case where a user deletes their team but keeps the claims.
     */
    private static void removeMarkersForDeletedTeams(ResourceKey<Level> dimension, String dimensionId,
                                                      Set<UUID> teamsWithClaims) {
        Map<UUID, Set<String>> dimensionMarkers = activeMarkersByDimension.get(dimensionId);
        if (dimensionMarkers == null) {
            return;
        }

        Set<UUID> trackedTeams = new HashSet<>(dimensionMarkers.keySet());

        for (UUID teamId : trackedTeams) {
            if (!teamsWithClaims.contains(teamId)) {
                continue;
            }

            Optional<dev.ftb.mods.ftbteams.api.Team> team = claimProvider.getTeamById(teamId);
            if (team.isEmpty()) {
                LOGGER.info("Team {} no longer exists, removing markers", teamId);
                removeAllTeamMarkers(dimension, teamId);
                cachedTeamColors.remove(teamId);
            }
        }
    }

    /**
     * Checks if the team's color has changed since the last update.
     * Updates the cached color if a change is detected.
     */
    private static boolean hasTeamColorChanged(UUID teamId, List<ClaimRegion> regions) {
        if (regions.isEmpty()) {
            return false;
        }

        int currentColor = regions.getFirst().getTeamColor();
        Integer cachedColor = cachedTeamColors.get(teamId);

        if (cachedColor == null) {
            cachedTeamColors.put(teamId, currentColor);
            return false;
        }

        if (!cachedColor.equals(currentColor)) {
            cachedTeamColors.put(teamId, currentColor);
            return true;
        }

        return false;
    }

    /**
     * Creates a new MarkerDiff that forces all existing markers to be rebuilt.
     */
    private static MarkerDiff forceFullRebuild(MarkerDiff original) {
        Set<String> allToRebuild = new HashSet<>(original.toUpdate);
        allToRebuild.addAll(original.toRebuild);

        return new MarkerDiff(
                original.toAdd,
                Collections.emptySet(),
                allToRebuild,
                original.toRemove
        );
    }

    private static String getDimensionId(ResourceKey<Level> dimension) {
        return dimension.location().toString();
    }

    /**
     * Clears all cached data. Should be called during server shutdown.
     */
    public static void clearAll() {
        activeMarkersByDimension.clear();
        holeSignaturesByDimension.clear();
        cachedTeamColors.clear();
        LOGGER.debug("Cleared all ClaimRenderer caches");
    }

    // ==================== Inner Classes ====================

    /**
     * Represents the differences between current and desired marker states.
     */
    private record MarkerDiff(
            Set<String> toAdd,
            Set<String> toUpdate,
            Set<String> toRebuild,
            Set<String> toRemove
    ) {}

    /**
     * Unique identifier for a chunk position within a dimension.
     */
    private record ChunkKey(ResourceKey<Level> dimension, int x, int z) {
        static ChunkKey from(ChunkDimPos pos) {
            return new ChunkKey(pos.dimension(), pos.x(), pos.z());
        }
    }
}

