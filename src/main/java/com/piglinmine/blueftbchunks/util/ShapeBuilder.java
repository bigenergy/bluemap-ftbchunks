package com.piglinmine.blueftbchunks.util;

/*
 * This file is part of BMUtils, licensed under the MPL2 License (MPL).
 * Please keep tabs on https://github.com/TechnicJelle/BMUtils for updates.
 *
 * Copyright (c) TechnicJelle <https://technicjelle.com>
 * Copyright (c) contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector2i;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Shape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;

/**
 * A {@link Shape} with possibly some holes.
 */
public class ShapeBuilder {
    private static final Vector2d CHUNK_CELL_SIZE = Vector2d.from(16, 16);

    private final Shape shape;
    private final Collection<Shape> holes;

    /**
     * Creates a new ShapeBuilder with no holes.
     *
     * @param shape The outer shape of the cheese.
     */
    public ShapeBuilder(Shape shape) {
        this.shape = shape;
        this.holes = Collections.emptyList();
    }

    /**
     * Creates a new ShapeBuilder with holes.
     *
     * @param shape The outer shape of the cheese.
     * @param holes Any holes in the cheese.
     */
    public ShapeBuilder(Shape shape, Shape... holes) {
        this.shape = shape;
        this.holes = Arrays.asList(holes);
    }

    /**
     * Creates a new ShapeBuilder with holes.
     *
     * @param shape The outer shape of the cheese.
     * @param holes Any holes in the cheese.
     */
    public ShapeBuilder(Shape shape, Collection<Shape> holes) {
        this.shape = shape;
        this.holes = holes;
    }

    /**
     * @return The outer shape of the cheese.
     */
    public Shape getShape() {
        return shape;
    }

    /**
     * @return Any holes in the cheese.
     */
    public Collection<Shape> getHoles() {
        return holes;
    }

    /**
     * Creates BlueMap {@link Shape}s with potentially some holes, from a collection of chunks.<br>
     * Designed to be looped over and fed into a BlueMap {@link ShapeMarker} or {@link ExtrudeMarker}
     *
     * @param chunks The chunks to create the cheese from.
     * @return A collection of {@link ShapeBuilder} objects, each representing a single connected area.
     */
    public static Collection<ShapeBuilder> createPlatterFromChunks(Vector2i... chunks) {
        return createPlatterFromCells(CHUNK_CELL_SIZE, chunks);
    }

    /**
     * Creates a single BlueMap {@link Shape} with potentially some holes, from a collection of chunks.<br>
     * Designed to be fed directly into a BlueMap {@link ShapeMarker} or {@link ExtrudeMarker}.<br>
     *
     * <h4>WARNING:</h4><b>This method assumes that all chunks are connected!</b><br>
     * If you're not absolutely 100% sure that your chunks will always be connected, use {@link #createPlatterFromChunks(Vector2i...)} instead.<br>
     * If multiple disconnected areas are found, only the first one will be returned.
     *
     * @param chunks The chunks to create the cheese from.
     * @return A single {@link ShapeBuilder} object representing a single connected area.
     */
    public static ShapeBuilder createSingleFromChunks(Vector2i... chunks) {
        return createSingleFromCells(CHUNK_CELL_SIZE, chunks);
    }

    /**
     * Creates BlueMap {@link Shape}s with potentially some holes, from a collection of custom-sized cells.<br>
     * Designed to be looped over and fed into a BlueMap {@link ShapeMarker} or {@link ExtrudeMarker}
     *
     * @param cellSize The size of a single cell.
     * @param cells    The cells to create the cheese from.
     * @return A collection of {@link ShapeBuilder} objects, each representing a single connected area.
     */
    public static Collection<ShapeBuilder> createPlatterFromCells(Vector2d cellSize, Vector2i... cells) {
        Set<Vector2i> remainingCells = new HashSet<>(List.of(cells));
        List<ShapeBuilder> platter = new ArrayList<>();

        while (!remainingCells.isEmpty()) {
            Set<Vector2i> connectedCells = new HashSet<>();
            Stack<Vector2i> toVisit = new Stack<>();

            // start with the first cell:
            Vector2i start = remainingCells.iterator().next();
            toVisit.push(start);

            while (!toVisit.isEmpty()) {
                Vector2i current = toVisit.pop();

                if (!remainingCells.contains(current)) continue;
                remainingCells.remove(current);
                connectedCells.add(current);

                // add all neighbours that are in the remainingCells set and not visited yet
                for (Direction direction : Direction.values()) {
                    Vector2i neighbour = current.add(direction.vector);
                    if (remainingCells.contains(neighbour)) {
                        toVisit.add(neighbour);
                    }
                }
            }

            platter.add(createSingleFromCells(cellSize, connectedCells.toArray(Vector2i[]::new)));
        }

        return platter;
    }

    /**
     * Creates a single BlueMap {@link Shape} with potentially some holes, from a collection of custom-sized cells.<br>
     * Designed to be fed directly into a BlueMap {@link ShapeMarker} or {@link ExtrudeMarker}.<br>
     *
     * <h4>WARNING:</h4><b>This method assumes that all cells are connected!</b><br>
     * If you're not absolutely 100% sure that your cells will always be connected, use {@link #createPlatterFromCells(Vector2d, Vector2i...)} instead.<br>
     * If multiple disconnected areas are found, only the first one will be returned.
     *
     * @param cellSize The size of a single cell.
     * @param cells    The cells to create the cheese from.
     * @return A single {@link ShapeBuilder} object representing a single connected area.
     */
    public static ShapeBuilder createSingleFromCells(Vector2d cellSize, Vector2i... cells) {
        Set<Edge> edges = createEdgesFromCells(cells);

        // find all edges that don't have a second edge in the opposite direction (flipped)
        // and add them to a multi-value-map indexed by the edges starting-point
        // the map is sorted so that the first key is guaranteed to be on the outer edge
        TreeMap<Vector2i, EnumMap<Direction, Edge>> borders = new TreeMap<>((a, b) -> {
            int c = a.getX() - b.getX();
            return c != 0 ? c : a.getY() - b.getY();
        });
        edges.stream()
                .filter(edge -> !edges.contains(edge.flip))
                .forEach(edge -> borders
                        .computeIfAbsent(edge.from, k -> new EnumMap<>(Direction.class))
                        .put(edge.direction, edge)
                );

        // the first trace will always be the outer outline
        Shape outline = createShape(trace(borders, true), cellSize);

        // all following traces will be holes
        List<Shape> holes = new LinkedList<>();
        while (!borders.isEmpty()) {
            holes.add(createShape(trace(borders, false), cellSize));
        }

        return new ShapeBuilder(outline, holes);
    }

    /**
     * Create edges around each cell, in clockwise direction
     */
    private static Set<Edge> createEdgesFromCells(Vector2i... cells) {
        Set<Edge> edges = new HashSet<>(cells.length * 4);

        for (Vector2i cell : cells) {
            Vector2i[] corners = new Vector2i[]{
                    cell,
                    cell.add(1, 0),
                    cell.add(1, 1),
                    cell.add(0, 1)
            };

            edges.add(new Edge(corners[0], corners[1]));
            edges.add(new Edge(corners[1], corners[2]));
            edges.add(new Edge(corners[2], corners[3]));
            edges.add(new Edge(corners[3], corners[0]));
        }

        return edges;
    }

    /**
     * Trace a line by following the first edge until we reach the starting point again
     */
    private static List<Vector2i> trace(TreeMap<Vector2i, EnumMap<Direction, Edge>> borders, boolean clockwiseFirst) {
        List<Vector2i> line = new LinkedList<>();
        Vector2i start = borders.firstKey();
        Vector2i position = start;
        Direction direction = Direction.UP; // start direction doesn't matter
        do {
            EnumMap<Direction, Edge> connectingEdges = borders.get(position);

            if (connectingEdges == null || connectingEdges.isEmpty())
                throw new IllegalStateException("Loose end"); // should never happen

            // Take the next edge & remove it from the source-set in the process
            // Choosing the edge by taking the direction of the previous edge and trying to find the most clockwise
            // (or counterclockwise, if specified) turn possible
            Direction d = direction.opposite;
            Edge edge = null;
            while (edge == null) {
                d = clockwiseFirst ? d.counterClockwise : d.clockwise;
                edge = connectingEdges.remove(d);
            }

            // add the position to the line and advance
            line.add(position);
            position = edge.to;
            direction = edge.direction;

            // optimize
            optimizeEnd(line);

        } while (!position.equals(start));

        // optimize one last time with the start moved to the end
        line.add(line.remove(0));
        optimizeEnd(line);

        // remove all empty multi-value-map collections
        borders.values().removeIf(Map::isEmpty);

        return line;
    }

    /**
     * line-optimization: if the last three points are on a straight line, remove the middle one
     */
    private static void optimizeEnd(List<Vector2i> line) {
        int s = line.size();
        if (s >= 3) {
            Vector2i from = line.get(s - 3);
            Vector2i middle = line.get(s - 2);
            Vector2i to = line.get(s - 1);
            if ( // we can safely assume that all edges are axis-aligned
                    (from.getX() == middle.getX() && to.getX() == middle.getX()) ||
                            (from.getY() == middle.getY() && to.getY() == middle.getY())
            ) {
                line.remove(s - 2);
            }
        }
    }

    /**
     * Translates the cell-positions to world-positions by applying the cellSize
     */
    private static Shape createShape(List<Vector2i> cellPositions, Vector2d cellSize) {
        return new Shape(cellPositions.stream()
                .map(position -> position.toDouble().mul(cellSize))
                .toArray(Vector2d[]::new));
    }

    private static class Edge {
        private final Vector2i from, to;
        private final Direction direction;
        private final Edge flip;

        public Edge(Vector2i from, Vector2i to) {
            if (from.equals(to)) throw new IllegalArgumentException("from and to can not be the same");

            this.from = from;
            this.to = to;
            this.direction = from.getX() == to.getX() ?
                    from.getY() > to.getY() ? Direction.DOWN : Direction.UP :
                    from.getX() > to.getX() ? Direction.LEFT : Direction.RIGHT;
            this.flip = new Edge(this);
        }

        private Edge(Edge flip) {
            this.from = flip.to;
            this.to = flip.from;
            this.direction = flip.direction.opposite;
            this.flip = flip;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Edge edge = (Edge) o;

            if (!from.equals(edge.from)) return false;
            return to.equals(edge.to);
        }

        @Override
        public int hashCode() {
            int result = from.hashCode();
            result = 31 * result + to.hashCode();
            return result;
        }

    }

    private enum Direction {
        UP,
        RIGHT,
        DOWN,
        LEFT;

        static {
            UP.clockwise = RIGHT;
            RIGHT.clockwise = DOWN;
            DOWN.clockwise = LEFT;
            LEFT.clockwise = UP;

            UP.counterClockwise = LEFT;
            LEFT.counterClockwise = DOWN;
            DOWN.counterClockwise = RIGHT;
            RIGHT.counterClockwise = UP;

            UP.opposite = DOWN;
            LEFT.opposite = RIGHT;
            DOWN.opposite = UP;
            RIGHT.opposite = LEFT;

            UP.vector = Vector2i.from(0, 1);
            RIGHT.vector = Vector2i.from(1, 0);
            DOWN.vector = Vector2i.from(0, -1);
            LEFT.vector = Vector2i.from(-1, 0);
        }

        private Direction clockwise;
        private Direction counterClockwise;
        private Direction opposite;

        public Vector2i vector;
    }
}