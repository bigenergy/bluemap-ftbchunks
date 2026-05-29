package com.piglinmine.blueftbchunks.data;

import java.util.*;

/**
 * Data model representing persisted chunk claim information.
 *
 * <p>This class is designed for JSON serialization/deserialization and contains
 * all claim data organized by dimension and team.</p>
 */
public class ChunkClaimData {

    /** Version number for data format compatibility. */
    private int version = 1;

    /** Timestamp of last save operation. */
    private long lastSaved = 0;

    /** Map of dimension ID -> team claims in that dimension. */
    private Map<String, DimensionClaims> dimensions = new HashMap<>();

    public ChunkClaimData() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public long getLastSaved() {
        return lastSaved;
    }

    public void setLastSaved(long lastSaved) {
        this.lastSaved = lastSaved;
    }

    public Map<String, DimensionClaims> getDimensions() {
        return dimensions;
    }

    public void setDimensions(Map<String, DimensionClaims> dimensions) {
        this.dimensions = dimensions;
    }

    /**
     * Gets or creates dimension claims for the specified dimension.
     *
     * @param dimensionId the dimension identifier (e.g., "minecraft:overworld")
     * @return the dimension claims object
     */
    public DimensionClaims getOrCreateDimension(String dimensionId) {
        return dimensions.computeIfAbsent(dimensionId, k -> new DimensionClaims());
    }

    /**
     * Clears all stored data.
     */
    public void clear() {
        dimensions.clear();
        lastSaved = 0;
    }

    /**
     * Gets total count of claimed chunks across all dimensions.
     *
     * @return the total chunk count
     */
    public int getTotalChunkCount() {
        return dimensions.values().stream()
                .mapToInt(DimensionClaims::getTotalChunkCount)
                .sum();
    }

    /**
     * Gets total count of teams with claims across all dimensions.
     *
     * @return the total team count
     */
    public int getTotalTeamCount() {
        Set<String> uniqueTeams = new HashSet<>();
        for (DimensionClaims dim : dimensions.values()) {
            uniqueTeams.addAll(dim.getTeams().keySet());
        }
        return uniqueTeams.size();
    }

    /**
     * Represents all claims within a single dimension.
     */
    public static class DimensionClaims {

        /** Map of team UUID (as string) -> team claim data. */
        private Map<String, TeamClaimData> teams = new HashMap<>();

        public DimensionClaims() {
        }

        public Map<String, TeamClaimData> getTeams() {
            return teams;
        }

        public void setTeams(Map<String, TeamClaimData> teams) {
            this.teams = teams;
        }

        /**
         * Gets or creates team claim data for the specified team.
         *
         * @param teamId the team UUID as a string
         * @return the team claim data
         */
        public TeamClaimData getOrCreateTeam(String teamId) {
            return teams.computeIfAbsent(teamId, k -> new TeamClaimData());
        }

        /**
         * Removes a team from this dimension.
         *
         * @param teamId the team UUID as a string
         */
        public void removeTeam(String teamId) {
            teams.remove(teamId);
        }

        /**
         * Gets total chunk count in this dimension.
         *
         * @return the chunk count
         */
        public int getTotalChunkCount() {
            return teams.values().stream()
                    .mapToInt(t -> t.getChunks().size())
                    .sum();
        }
    }

    /**
     * Represents claim data for a single team.
     */
    public static class TeamClaimData {

        /** Team display name. */
        private String teamName = "Unknown";

        /** Team color as RGB integer. */
        private int teamColor = 0xFFFFFF;

        /** Set of claimed chunk positions in "x,z" format. */
        private Set<String> chunks = new HashSet<>();

        /** Whether the team has force-loaded chunks. */
        private boolean hasForceLoaded = false;

        public TeamClaimData() {
        }

        public String getTeamName() {
            return teamName;
        }

        public void setTeamName(String teamName) {
            this.teamName = teamName;
        }

        public int getTeamColor() {
            return teamColor;
        }

        public void setTeamColor(int teamColor) {
            this.teamColor = teamColor;
        }

        public Set<String> getChunks() {
            return chunks;
        }

        public void setChunks(Set<String> chunks) {
            this.chunks = chunks;
        }

        public boolean isHasForceLoaded() {
            return hasForceLoaded;
        }

        public void setHasForceLoaded(boolean hasForceLoaded) {
            this.hasForceLoaded = hasForceLoaded;
        }

        /**
         * Adds a chunk position to this team's claims.
         *
         * @param chunkX the chunk X coordinate
         * @param chunkZ the chunk Z coordinate
         */
        public void addChunk(int chunkX, int chunkZ) {
            chunks.add(formatChunkKey(chunkX, chunkZ));
        }

        /**
         * Removes a chunk position from this team's claims.
         *
         * @param chunkX the chunk X coordinate
         * @param chunkZ the chunk Z coordinate
         */
        public void removeChunk(int chunkX, int chunkZ) {
            chunks.remove(formatChunkKey(chunkX, chunkZ));
        }

        /**
         * Checks if a chunk is claimed by this team.
         *
         * @param chunkX the chunk X coordinate
         * @param chunkZ the chunk Z coordinate
         * @return true if the chunk is claimed
         */
        public boolean hasChunk(int chunkX, int chunkZ) {
            return chunks.contains(formatChunkKey(chunkX, chunkZ));
        }

        /**
         * Formats chunk coordinates as a storage key.
         *
         * @param chunkX the chunk X coordinate
         * @param chunkZ the chunk Z coordinate
         * @return the formatted key
         */
        public static String formatChunkKey(int chunkX, int chunkZ) {
            return chunkX + "," + chunkZ;
        }

        /**
         * Parses a chunk key into coordinates.
         *
         * @param key the chunk key in "x,z" format
         * @return an array of [x, z] coordinates, or null if invalid
         */
        public static int[] parseChunkKey(String key) {
            try {
                String[] parts = key.split(",");
                if (parts.length == 2) {
                    return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
                }
            } catch (NumberFormatException ignored) {
            }
            return null;
        }
    }
}

