package com.piglinmine.blueftbchunks.bluemap;

import com.piglinmine.blueftbchunks.api.MapMarkerService;
import com.piglinmine.blueftbchunks.ftbchunks.region.ClaimRegion;
import com.piglinmine.blueftbchunks.util.ShapeBuilder;
import com.flowpowered.math.vector.Vector2i;
import com.mojang.logging.LogUtils;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of {@link MapMarkerService} for BlueMap integration.
 * Handles the creation, update, and removal of both 2D and 3D markers on BlueMap.
 *
 * <p>Two marker sets are created:</p>
 * <ul>
 *   <li><b>3D Claims</b> - Extruded markers that show the full height of claims</li>
 *   <li><b>2D Claims</b> - Flat shape markers that remain at a consistent Y level</li>
 * </ul>
 */
public final class BlueMapMarkerService implements MapMarkerService {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 3D Marker Set (Extruded)
    private static final String MARKER_SET_ID = "ftbchunks.claims.3d";
    private static final String MARKER_SET_LABEL = "FTB Chunks Claims (3D)";

    // 2D Marker Set (Flat)
    private static final String MARKER_SET_ID_2D = "ftbchunks.claims.2d";
    private static final String MARKER_SET_LABEL_2D = "FTB Chunks Claims (2D)";

    private static final float FILL_ALPHA = 0.2f;
    private static final float LINE_ALPHA = 0.6f;

    // 3D marker height range
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    // 2D marker consistent Y level
    private static final float FLAT_Y = 65.0f;

    private static final BlueMapMarkerService INSTANCE = new BlueMapMarkerService();

    private BlueMapMarkerService() {
    }

    /**
     * Returns the singleton instance of the BlueMap marker service.
     *
     * @return the singleton instance
     */
    public static BlueMapMarkerService getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean createMarker(ResourceKey<Level> dimension, ClaimRegion region) {
        if (region == null) {
            return false;
        }

        Optional<BlueMapWorld> worldOpt = getBlueMapWorld(dimension);
        if (worldOpt.isEmpty()) {
            LOGGER.debug("BlueMap world not available for dimension: {}", dimension.location());
            return false;
        }

        boolean created = false;
        for (BlueMapMap map : worldOpt.get().getMaps()) {
            // Create 3D extruded marker
            MarkerSet markerSet3D = getOrCreateMarkerSet3D(map);
            ExtrudeMarker marker3D = buildExtrudeMarker(region);
            markerSet3D.put(region.getMarkerId(), marker3D);

            // Create 2D flat marker
            MarkerSet markerSet2D = getOrCreateMarkerSet2D(map);
            ShapeMarker marker2D = buildShapeMarker(region);
            markerSet2D.put(region.getMarkerId(), marker2D);

            LOGGER.debug("Created 2D and 3D markers {} on map {}", region.getMarkerId(), map.getId());
            created = true;
        }
        return created;
    }

    @Override
    public void updateMarker(ResourceKey<Level> dimension, ClaimRegion region) {
        if (region == null) {
            return;
        }

        getBlueMapWorld(dimension).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                // Update 3D marker
                MarkerSet markerSet3D = map.getMarkerSets().get(MARKER_SET_ID);
                if (markerSet3D != null && markerSet3D.get(region.getMarkerId()) instanceof ExtrudeMarker existingMarker3D) {
                    updateExistingExtrudeMarker(existingMarker3D, region);
                }

                // Update 2D marker
                MarkerSet markerSet2D = map.getMarkerSets().get(MARKER_SET_ID_2D);
                if (markerSet2D != null && markerSet2D.get(region.getMarkerId()) instanceof ShapeMarker existingMarker2D) {
                    updateExistingShapeMarker(existingMarker2D, region);
                }
            }
        });
    }

    @Override
    public void removeMarkers(ResourceKey<Level> dimension, Set<String> markerIds) {
        if (markerIds == null || markerIds.isEmpty()) {
            return;
        }

        getBlueMapWorld(dimension).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                // Remove from 3D marker set
                MarkerSet markerSet3D = map.getMarkerSets().get(MARKER_SET_ID);
                if (markerSet3D != null) {
                    markerIds.forEach(markerSet3D::remove);
                }

                // Remove from 2D marker set
                MarkerSet markerSet2D = map.getMarkerSets().get(MARKER_SET_ID_2D);
                if (markerSet2D != null) {
                    markerIds.forEach(markerSet2D::remove);
                }
            }
        });
    }

    @Override
    public void removeAllMarkersForTeam(ResourceKey<Level> dimension, UUID teamId) {
        LOGGER.debug("Removing all markers for team {} in dimension {}", teamId, dimension.location());
    }

    /**
     * Removes all markers in the claim marker set for a dimension.
     *
     * @param dimension the dimension to clear markers from
     */
    public void removeAllMarkers(ResourceKey<Level> dimension) {
        getBlueMapWorld(dimension).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                MarkerSet markerSet = map.getMarkerSets().get(MARKER_SET_ID);
                if (markerSet != null) {
                    // Get all marker IDs and remove them
                    Set<String> markerIds = new java.util.HashSet<>(markerSet.getMarkers().keySet());
                    markerIds.forEach(markerSet::remove);
                    LOGGER.debug("Removed {} markers from map {}", markerIds.size(), map.getId());
                }

                // Also remove 2D marker set if it exists
                MarkerSet markerSet2D = map.getMarkerSets().get(MARKER_SET_ID_2D);
                if (markerSet2D != null) {
                    Set<String> markerIds2D = new java.util.HashSet<>(markerSet2D.getMarkers().keySet());
                    markerIds2D.forEach(markerSet2D::remove);
                    LOGGER.debug("Removed {} 2D markers from map {}", markerIds2D.size(), map.getId());
                }
            }
        });
    }

    @Override
    public boolean isAvailable() {
        return BlueMapAPI.getInstance().isPresent();
    }

    @Override
    public String getMarkerSetId() {
        return MARKER_SET_ID;
    }

    /**
     * Updates multiple markers in place for efficiency.
     *
     * @param dimension   the dimension containing the markers
     * @param regions     the regions to update
     * @param markerIds   the set of marker IDs to update
     */
    public void updateMarkersInPlace(ResourceKey<Level> dimension,
                                      Map<String, ClaimRegion> regions,
                                      Set<String> markerIds) {
        getBlueMapWorld(dimension).ifPresent(world -> {
            for (BlueMapMap map : world.getMaps()) {
                // Update 3D markers
                MarkerSet markerSet3D = map.getMarkerSets().get(MARKER_SET_ID);
                if (markerSet3D != null) {
                    for (String markerId : markerIds) {
                        if (markerSet3D.get(markerId) instanceof ExtrudeMarker existingMarker) {
                            ClaimRegion region = regions.get(markerId);
                            if (region != null) {
                                updateExistingExtrudeMarker(existingMarker, region);
                            }
                        }
                    }
                }

                // Update 2D markers
                MarkerSet markerSet2D = map.getMarkerSets().get(MARKER_SET_ID_2D);
                if (markerSet2D != null) {
                    for (String markerId : markerIds) {
                        if (markerSet2D.get(markerId) instanceof ShapeMarker existingMarker) {
                            ClaimRegion region = regions.get(markerId);
                            if (region != null) {
                                updateExistingShapeMarker(existingMarker, region);
                            }
                        }
                    }
                }
            }
        });
    }

    private Optional<BlueMapWorld> getBlueMapWorld(ResourceKey<Level> dimension) {
        return BlueMapAPI.getInstance().flatMap(api -> api.getWorld(dimension));
    }

    private MarkerSet getOrCreateMarkerSet3D(BlueMapMap map) {
        return map.getMarkerSets().computeIfAbsent(
                MARKER_SET_ID,
                key -> MarkerSet.builder()
                        .label(MARKER_SET_LABEL)
                        .defaultHidden(false)
                        .build()
        );
    }

    private MarkerSet getOrCreateMarkerSet2D(BlueMapMap map) {
        return map.getMarkerSets().computeIfAbsent(
                MARKER_SET_ID_2D,
                key -> MarkerSet.builder()
                        .label(MARKER_SET_LABEL_2D)
                        .defaultHidden(true)
                        .build()
        );
    }

    private ExtrudeMarker buildExtrudeMarker(ClaimRegion region) {
        Vector2i[] chunks = region.getChunkCoordinates();
        ShapeBuilder shapeBuilder = ShapeBuilder.createSingleFromChunks(chunks);

        Color fillColor = new Color(region.getTeamColor(), FILL_ALPHA);
        Color lineColor = new Color(region.getTeamColor(), LINE_ALPHA);

        Shape[] holes = shapeBuilder.getHoles().toArray(Shape[]::new);

        return ExtrudeMarker.builder()
                .label(region.getLabel())
                .detail(region.getDetail())
                .shape(shapeBuilder.getShape(), MIN_Y, MAX_Y)
                .holes(holes)
                .fillColor(fillColor)
                .lineColor(lineColor)
                .build();
    }

    private ShapeMarker buildShapeMarker(ClaimRegion region) {
        Vector2i[] chunks = region.getChunkCoordinates();
        ShapeBuilder shapeBuilder = ShapeBuilder.createSingleFromChunks(chunks);

        Color fillColor = new Color(region.getTeamColor(), FILL_ALPHA);
        Color lineColor = new Color(region.getTeamColor(), LINE_ALPHA);

        return ShapeMarker.builder()
                .label(region.getLabel())
                .detail(region.getDetail())
                .shape(shapeBuilder.getShape(), FLAT_Y)
                .fillColor(fillColor)
                .lineColor(lineColor)
                .depthTestEnabled(false)
                .build();
    }

    private void updateExistingExtrudeMarker(ExtrudeMarker marker, ClaimRegion region) {
        Vector2i[] chunks = region.getChunkCoordinates();
        ShapeBuilder shapeBuilder = ShapeBuilder.createSingleFromChunks(chunks);

        Color fillColor = new Color(region.getTeamColor(), FILL_ALPHA);
        Color lineColor = new Color(region.getTeamColor(), LINE_ALPHA);

        marker.setLabel(region.getLabel());
        marker.setDetail(region.getDetail());
        marker.setShape(shapeBuilder.getShape(), MIN_Y, MAX_Y);
        marker.setFillColor(fillColor);
        marker.setLineColor(lineColor);
    }

    private void updateExistingShapeMarker(ShapeMarker marker, ClaimRegion region) {
        Vector2i[] chunks = region.getChunkCoordinates();
        ShapeBuilder shapeBuilder = ShapeBuilder.createSingleFromChunks(chunks);

        Color fillColor = new Color(region.getTeamColor(), FILL_ALPHA);
        Color lineColor = new Color(region.getTeamColor(), LINE_ALPHA);

        marker.setLabel(region.getLabel());
        marker.setDetail(region.getDetail());
        marker.setShape(shapeBuilder.getShape(), FLAT_Y);
        marker.setFillColor(fillColor);
        marker.setLineColor(lineColor);
    }
}

