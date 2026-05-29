package com.piglinmine.blueftbchunks.ftbchunks;

import com.piglinmine.blueftbchunks.api.ChunkClaimProvider;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ChunkClaimProvider} that retrieves claim data from FTB Chunks.
 * This class serves as the bridge between the mod's generic API and FTB Chunks' specific implementation.
 */
public final class FTBChunksClaimProvider implements ChunkClaimProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_TEAM_COLOR = 0xFFFFFF;
    private static final FTBChunksClaimProvider INSTANCE = new FTBChunksClaimProvider();

    private FTBChunksClaimProvider() {
        // Use lazy initialization via getters to avoid holding stale references
    }

    /**
     * Returns the singleton instance of the FTB Chunks claim provider.
     *
     * @return the singleton instance
     */
    public static FTBChunksClaimProvider getInstance() {
        return INSTANCE;
    }

    /**
     * Gets the FTB Chunks API lazily to avoid holding stale references.
     */
    private FTBChunksAPI.API getChunksApi() {
        return FTBChunksAPI.api();
    }

    /**
     * Gets the FTB Teams manager lazily to avoid holding stale references.
     *
     * @return the team manager instance
     */
    public TeamManager getTeamManager() {
        return FTBTeamsAPI.api().getManager();
    }

    @Override
    public Map<UUID, List<ClaimedChunk>> getClaimedChunksByTeam(ResourceKey<Level> dimension) {
        if (dimension == null) {
            return Collections.emptyMap();
        }

        try {
            Collection<ClaimedChunk> allChunks = getAllClaimedChunks();

            return allChunks.stream()
                    .filter(this::isValidChunk)
                    .filter(chunk -> dimension.equals(chunk.getPos().dimension()))
                    .collect(Collectors.groupingBy(
                            chunk -> chunk.getTeamData().getTeam().getTeamId(),
                            Collectors.toList()
                    ));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    @Override
    public List<ClaimedChunk> getClaimedChunksForTeam(ResourceKey<Level> dimension, UUID teamId) {
        if (dimension == null || teamId == null) {
            return Collections.emptyList();
        }

        try {
            Collection<ClaimedChunk> allChunks = getAllClaimedChunks();

            return allChunks.stream()
                    .filter(this::isValidChunk)
                    .filter(chunk -> dimension.equals(chunk.getPos().dimension()))
                    .filter(chunk -> matchesTeam(chunk, teamId))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<Team> getTeamById(UUID teamId) {
        if (teamId == null) {
            return Optional.empty();
        }

        try {
            return getTeamManager().getTeamByID(teamId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public int getTeamColor(Team team) {
        if (team == null) {
            return DEFAULT_TEAM_COLOR;
        }

        try {
            TextColor color = team.getColoredName().getStyle().getColor();
            return color != null ? color.getValue() : DEFAULT_TEAM_COLOR;
        } catch (Exception e) {
            return DEFAULT_TEAM_COLOR;
        }
    }

    @Override
    public String getTeamDisplayName(Team team) {
        if (team == null) {
            return "Unknown";
        }

        String shortName = team.getShortName();
        if (shortName == null || shortName.isBlank()) {
            return "Unknown";
        }

        int hashIndex = shortName.indexOf('#');
        return hashIndex > 0 ? shortName.substring(0, hashIndex) : shortName;
    }

    @SuppressWarnings("unchecked")
    private Collection<ClaimedChunk> getAllClaimedChunks() {
        try {
            FTBChunksAPI.API api = getChunksApi();
            if (api == null || api.getManager() == null) {
                LOGGER.debug("FTB Chunks API or manager not available yet");
                return Collections.emptyList();
            }
            Collection<ClaimedChunk> chunks = (Collection<ClaimedChunk>) api.getManager().getAllClaimedChunks();
            return chunks != null ? chunks : Collections.emptyList();
        } catch (Exception e) {
            LOGGER.debug("Error getting claimed chunks: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean isValidChunk(ClaimedChunk chunk) {
        return chunk != null && chunk.getPos() != null;
    }

    private boolean matchesTeam(ClaimedChunk chunk, UUID teamId) {
        try {
            return teamId.equals(chunk.getTeamData().getTeam().getTeamId());
        } catch (Exception e) {
            return false;
        }
    }
}

