package com.piglinmine.blueftbchunks.ftbchunks.region;

import com.piglinmine.blueftbchunks.ftbchunks.FTBChunksClaimProvider;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Groups disconnected sets of claimed chunks into contiguous regions using flood-fill.
 *
 * <p>This class is responsible for identifying connected groups of chunks that belong
 * to the same team and creating {@link ClaimRegion} objects for each group. It uses
 * a breadth-first flood-fill algorithm to identify contiguous chunk areas.</p>
 *
 * <p>Region numbers are assigned persistently to ensure stable marker IDs across
 * updates. When chunks are added or removed, the regions maintain their identities
 * as long as they contain the same chunks.</p>
 */
public final class RegionGrouper {

    private static final Map<UUID, Map<String, Integer>> teamRegionNumbers = new HashMap<>();
    private static final Map<UUID, Integer> teamNextRegionNumber = new HashMap<>();

    private static final int[][] NEIGHBOR_OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private RegionGrouper() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Groups a collection of claimed chunks into contiguous regions.
     *
     * <p>Uses a flood-fill algorithm to identify connected groups of chunks,
     * then creates a {@link ClaimRegion} for each group with a stable region number.</p>
     *
     * @param chunks    the chunks to group; must belong to the same team
     * @param teamId    the UUID of the team owning the chunks
     * @param dimension the dimension containing the chunks
     * @return a list of ClaimRegion objects, one for each contiguous group; empty if no chunks
     */
    public static List<ClaimRegion> groupChunksIntoRegions(List<ClaimedChunk> chunks,
                                                            UUID teamId,
                                                            ResourceKey<Level> dimension) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        Team team = chunks.getFirst().getTeamData().getTeam();
        String teamName = FTBChunksClaimProvider.getInstance().getTeamDisplayName(team);
        int teamColor = FTBChunksClaimProvider.getInstance().getTeamColor(team);
        String dimensionId = dimension.location().toString();

        Set<ChunkDimPos> unprocessedChunks = chunks.stream()
                .map(ClaimedChunk::getPos)
                .collect(Collectors.toSet());

        List<ClaimRegion> regions = new ArrayList<>();

        while (!unprocessedChunks.isEmpty()) {
            Set<ChunkDimPos> connectedChunks = floodFillConnectedChunks(unprocessedChunks);
            String regionKey = createCanonicalRegionKey(connectedChunks);
            int regionNumber = getOrAssignRegionNumber(teamId, regionKey);

            ClaimRegion region = new ClaimRegion(team, dimensionId, teamName, teamColor, regionNumber);
            connectedChunks.forEach(region::addChunk);
            regions.add(region);
        }

        cleanupStaleRegionNumbers(teamId, regions);
        return regions;
    }

    /**
     * Clears all cached region numbers for a specific team.
     * Should be called when a team's claims are fully removed.
     *
     * @param teamId the UUID of the team to clear
     */
    public static void clearTeam(UUID teamId) {
        teamRegionNumbers.remove(teamId);
        teamNextRegionNumber.remove(teamId);
    }

    /**
     * Clears all cached region data for all teams.
     * Should be called during server shutdown.
     */
    public static void clearAll() {
        teamRegionNumbers.clear();
        teamNextRegionNumber.clear();
    }

    /**
     * Performs a flood-fill to find all connected chunks starting from an arbitrary seed.
     *
     * @param unprocessed the set of unprocessed chunks; will be modified to remove processed chunks
     * @return a set of connected ChunkDimPos objects
     */
    private static Set<ChunkDimPos> floodFillConnectedChunks(Set<ChunkDimPos> unprocessed) {
        Queue<ChunkDimPos> queue = new ArrayDeque<>();
        Set<ChunkDimPos> connectedChunks = new HashSet<>();

        ChunkDimPos seed = unprocessed.iterator().next();
        unprocessed.remove(seed);
        queue.add(seed);
        connectedChunks.add(seed);

        while (!queue.isEmpty()) {
            ChunkDimPos current = queue.poll();

            for (int[] offset : NEIGHBOR_OFFSETS) {
                ChunkDimPos neighbor = new ChunkDimPos(
                        current.dimension(),
                        current.x() + offset[0],
                        current.z() + offset[1]
                );

                if (unprocessed.remove(neighbor)) {
                    queue.add(neighbor);
                    connectedChunks.add(neighbor);
                }
            }
        }

        return connectedChunks;
    }

    /**
     * Creates a canonical string key for a set of chunks for region identification.
     *
     * @param chunks the set of ChunkDimPos objects
     * @return a canonical string key representing the region
     */
    private static String createCanonicalRegionKey(Set<ChunkDimPos> chunks) {
        String dimensionId = chunks.iterator().next().dimension().location().toString();

        String sortedCoordinates = chunks.stream()
                .map(pos -> pos.x() + ":" + pos.z())
                .sorted()
                .collect(Collectors.joining(";"));

        return dimensionId + "|" + sortedCoordinates;
    }

    /**
     * Retrieves or assigns a stable region number for a given region key.
     *
     * @param teamId    the UUID of the team
     * @param regionKey the canonical region key
     * @return the assigned region number
     */
    private static int getOrAssignRegionNumber(UUID teamId, String regionKey) {
        return teamRegionNumbers
                .computeIfAbsent(teamId, k -> new HashMap<>())
                .computeIfAbsent(regionKey, k -> {
                    int nextNumber = teamNextRegionNumber.getOrDefault(teamId, 0) + 1;
                    teamNextRegionNumber.put(teamId, nextNumber);
                    return nextNumber;
                });
    }

    /**
     * Cleans up stale region numbers that no longer correspond to current regions.
     *
     * @param teamId         the UUID of the team
     * @param currentRegions the list of current ClaimRegion objects
     */
    private static void cleanupStaleRegionNumbers(UUID teamId, List<ClaimRegion> currentRegions) {
        Map<String, Integer> regionNumbers = teamRegionNumbers.get(teamId);
        if (regionNumbers == null) {
            return;
        }

        Set<String> currentKeys = currentRegions.stream()
                .map(region -> createCanonicalRegionKey(
                        Arrays.stream(region.getChunks()).collect(Collectors.toSet())
                ))
                .collect(Collectors.toSet());

        regionNumbers.keySet().removeIf(key -> !currentKeys.contains(key));
    }
}