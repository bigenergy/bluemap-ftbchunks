package com.piglinmine.blueftbchunks.api;

import com.piglinmine.blueftbchunks.ftbchunks.region.ClaimRegion;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;

/**
 * Abstraction for rendering claim regions on a web map.
 * This interface decouples the claim processing logic from the specific map API being used,
 * allowing for different map implementations (e.g., BlueMap, Dynmap) to be supported.
 */
public interface MapMarkerService {

    /**
     * Creates a new marker for a claim region on the map.
     *
     * @param dimension the dimension where the region exists
     * @param region    the claim region to create a marker for
     * @return {@code true} if the marker was successfully created, {@code false} otherwise
     */
    boolean createMarker(ResourceKey<Level> dimension, ClaimRegion region);

    /**
     * Updates an existing marker's visual properties without recreating it.
     * This is more efficient than removing and recreating for minor changes.
     *
     * @param dimension the dimension containing the marker
     * @param region    the claim region with updated data
     */
    void updateMarker(ResourceKey<Level> dimension, ClaimRegion region);

    /**
     * Removes markers by their unique identifiers.
     *
     * @param dimension the dimension containing the markers
     * @param markerIds the set of marker IDs to remove
     */
    void removeMarkers(ResourceKey<Level> dimension, Set<String> markerIds);

    /**
     * Removes all markers associated with a specific team in a dimension.
     *
     * @param dimension the dimension to remove markers from
     * @param teamId    the UUID of the team whose markers should be removed
     */
    void removeAllMarkersForTeam(ResourceKey<Level> dimension, UUID teamId);

    /**
     * Checks if the map service is currently available and ready to use.
     *
     * @return {@code true} if the service is available, {@code false} otherwise
     */
    boolean isAvailable();

    /**
     * Gets the unique identifier for the marker set used by this service.
     *
     * @return the marker set ID
     */
    String getMarkerSetId();
}

