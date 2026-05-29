package com.piglinmine.blueftbchunks.ftbchunks;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;

/**
 * Handles FTB Chunks claim and unclaim events, forwarding them to the cache for processing.
 *
 * <p>This class acts as the bridge between FTB Chunks' event system and the mod's
 * claim rendering pipeline. It registers listeners for claim events and delegates
 * processing to the {@link ClaimCache}.</p>
 */
public class ClaimEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ClaimCache claimCache;

    /**
     * Creates a new event handler with the specified claim cache.
     *
     * @param claimCache the cache to forward events to; must not be null
     * @throws IllegalArgumentException if claimCache is null
     */
    public ClaimEventHandler(ClaimCache claimCache) {
        if (claimCache == null) {
            throw new IllegalArgumentException("ClaimCache cannot be null");
        }
        this.claimCache = claimCache;
    }

    /**
     * Registers this handler's event listeners with the FTB Chunks event bus.
     * Should be called during mod initialization when FTB Chunks integration is enabled.
     */
    public void register() {
        ClaimedChunkEvent.AFTER_CLAIM.register(this::handleChunkClaimed);
        ClaimedChunkEvent.AFTER_UNCLAIM.register(this::handleChunkUnclaimed);
        LOGGER.info("Registered FTB Chunks event handlers");
    }

    private void handleChunkClaimed(CommandSourceStack source, ClaimedChunk chunk) {
        if (chunk == null) {
            return;
        }

        LOGGER.debug("Chunk claimed at {}", chunk.getPos());
        delegateToCache(() -> claimCache.addClaim(chunk));
    }

    private void handleChunkUnclaimed(CommandSourceStack source, ClaimedChunk chunk) {
        if (chunk == null) {
            return;
        }

        LOGGER.debug("Chunk unclaimed at {}", chunk.getPos());
        delegateToCache(() -> claimCache.removeClaim(chunk));
    }

    private void delegateToCache(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            LOGGER.error("Error processing claim event", e);
        }
    }
}