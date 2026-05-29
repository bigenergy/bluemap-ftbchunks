package com.piglinmine.blueftbchunks.api;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Represents a single chunk position with its associated dimension.
 * This record provides a convenient way to uniquely identify a chunk
 * across dimensions using the chunk's X and Z coordinates.
 *
 * @param dimension the dimension key (e.g., overworld, nether, end)
 * @param chunkX    the chunk's X coordinate (not block coordinate)
 * @param chunkZ    the chunk's Z coordinate (not block coordinate)
 */
public record ChunkPosition(ResourceKey<Level> dimension, int chunkX, int chunkZ) {

    /**
     * Creates a ChunkPosition from an FTB Chunks ChunkDimPos.
     *
     * @param pos the FTB Chunks position to convert
     * @return a new ChunkPosition representing the same location
     */
    public static ChunkPosition fromChunkDimPos(ChunkDimPos pos) {
        return new ChunkPosition(pos.dimension(), pos.x(), pos.z());
    }

    /**
     * Converts chunk coordinates to the center block position within the chunk.
     *
     * @return an array containing [blockX, blockZ] at the center of this chunk
     */
    public int[] toCenterBlockPosition() {
        return new int[]{
                chunkX * 16 + 8,
                chunkZ * 16 + 8
        };
    }

    /**
     * Converts chunk coordinates to the corner block position (minimum X, Z).
     *
     * @return an array containing [blockX, blockZ] at the corner of this chunk
     */
    public int[] toCornerBlockPosition() {
        return new int[]{
                chunkX * 16,
                chunkZ * 16
        };
    }

    /**
     * Gets the dimension ID as a string (e.g., "minecraft:overworld").
     *
     * @return the dimension's resource location as a string
     */
    public String getDimensionId() {
        return dimension.location().toString();
    }
}

