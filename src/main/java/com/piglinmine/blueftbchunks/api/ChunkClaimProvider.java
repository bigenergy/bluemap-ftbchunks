package com.piglinmine.blueftbchunks.api;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstraction for retrieving chunk claim data from the underlying claiming system.
 * This interface allows for different claim data sources (e.g., FTB Chunks, other mods)
 * to be used interchangeably.
 */
public interface ChunkClaimProvider {

    /**
     * Retrieves all claimed chunks in a dimension, grouped by team.
     *
     * @param dimension the dimension to query for claims
     * @return a map where keys are team UUIDs and values are lists of claimed chunks
     *         belonging to that team; returns an empty map if no claims exist or an error occurs
     */
    Map<UUID, List<ClaimedChunk>> getClaimedChunksByTeam(ResourceKey<Level> dimension);

    /**
     * Retrieves all claimed chunks for a specific team in a dimension.
     *
     * @param dimension the dimension to query
     * @param teamId    the UUID of the team to filter by
     * @return a list of chunks claimed by the specified team; returns an empty list
     *         if the team has no claims or an error occurs
     */
    List<ClaimedChunk> getClaimedChunksForTeam(ResourceKey<Level> dimension, UUID teamId);

    /**
     * Retrieves a team by its unique identifier.
     *
     * @param teamId the UUID of the team to retrieve
     * @return an Optional containing the team if found, or empty if not found
     */
    Optional<Team> getTeamById(UUID teamId);

    /**
     * Gets the display color for a team.
     *
     * @param team the team to get the color for
     * @return the team's color as an RGB integer value, or a default color if unavailable
     */
    int getTeamColor(Team team);

    /**
     * Extracts a clean display name from a team's short name.
     * Typically removes discriminators or other suffixes.
     *
     * @param team the team to get the display name for
     * @return the cleaned team name suitable for display
     */
    String getTeamDisplayName(Team team);
}

