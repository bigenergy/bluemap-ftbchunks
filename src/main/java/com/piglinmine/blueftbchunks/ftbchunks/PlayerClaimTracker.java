package com.piglinmine.blueftbchunks.ftbchunks;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks player login and logout events to update team claim displays on BlueMap.
 *
 * <p>This class ensures that the online/offline status shown in claim marker details
 * stays current by refreshing team claims whenever a player joins or leaves.</p>
 *
 * <p>It maintains a cache of player-to-team mappings to enable efficient lookups
 * during logout events when the player's team association may no longer be
 * available through the API.</p>
 */
public class PlayerClaimTracker {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, UUID> playerTeamCache = new ConcurrentHashMap<>();

    private PlayerClaimTracker() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Handles player login events by updating their team's claim markers.
     *
     * @param event the login event
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        processPlayerLogin(player);
    }

    /**
     * Handles player logout events by updating their team's claim markers.
     *
     * @param event the logout event
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        processPlayerLogout(player);
    }

    /**
     * Clears the player-team mapping cache.
     * Should be called during server shutdown for cleanup.
     */
    public static void clearCache() {
        playerTeamCache.clear();
        LOGGER.debug("Cleared player-team cache");
    }

    /**
     * Returns the current size of the player-team cache.
     *
     * @return the number of cached player-team mappings
     */
    public static int getCacheSize() {
        return playerTeamCache.size();
    }

    // ==================== Private Helper Methods ====================

    private static void processPlayerLogin(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Optional<UUID> teamId = findPlayerTeamId(playerId);

        if (teamId.isEmpty()) {
            LOGGER.debug("Player {} logged in without a team", player.getGameProfile().getName());
            return;
        }

        playerTeamCache.put(playerId, teamId.get());
        LOGGER.debug("Player {} logged in, updating team {} claims",
                player.getGameProfile().getName(), teamId.get());

        refreshTeamClaimsInAllDimensions(player.getServer(), teamId.get());
    }

    private static void processPlayerLogout(ServerPlayer player) {
        UUID playerId = player.getUUID();
        String playerName = player.getGameProfile().getName();

        UUID teamId = playerTeamCache.remove(playerId);

        if (teamId == null) {
            teamId = findPlayerTeamId(playerId).orElse(null);
        }

        if (teamId == null) {
            LOGGER.debug("Player {} logged out without a team", playerName);
            return;
        }

        LOGGER.debug("Player {} logged out, updating team {} claims", playerName, teamId);

        MinecraftServer server = player.getServer();
        if (server != null) {
            refreshTeamClaimsInAllDimensions(server, teamId);
        }
    }

    private static Optional<UUID> findPlayerTeamId(UUID playerId) {
        return FTBChunksClaimProvider.getInstance()
                .getTeamManager()
                .getPlayerTeamForPlayerID(playerId)
                .map(Team::getTeamId);
    }

    private static void refreshTeamClaimsInAllDimensions(MinecraftServer server, UUID teamId) {
        if (server == null || teamId == null) {
            return;
        }

        try {
            for (ResourceKey<Level> dimension : server.levelKeys()) {
                ClaimRenderer.updateTeamClaims(dimension, teamId);
            }
        } catch (Exception e) {
            LOGGER.error("Error updating team claims for team {}", teamId, e);
        }
    }
}