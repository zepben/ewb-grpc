/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import com.zepben.profilemanager.exporters.diagrams.model.EdgeKind
import kotlin.math.hypot

/**
 * Replaces the orthogonal routes ELK gives associations with straight lines between the two classes where that is
 * clearly an improvement, as hand drawn UML diagrams often do. A straight line is only used if it:
 *  - doesn't pass through, or too close to, any other class,
 *  - doesn't run along or too close to any other line,
 *  - doesn't add any crossings, and
 *  - leaves room to place its role and cardinality labels next to each end without overlapping anything.
 *
 * Associations between the same two classes are straightened together, as evenly spaced parallel lines, or not at all.
 * Generalisations are left alone: they are drawn as orthogonal trees into each superclass.
 */
object AssociationStraightener {

    /** How far a straight line must stay from classes (other than its own) and other lines. */
    private const val CLEARANCE = 8.0

    /** Spacing between parallel associations joining the same two classes. */
    private const val PARALLEL_SPACING = 16.0

    /** Lines shorter than this are left as routed: there isn't room for labels on a short diagonal. */
    private const val MIN_LENGTH = 30.0

    fun straighten(layout: DiagramLayout): DiagramLayout {
        val boxes = layout.boxes
        var current = layout

        val groups = layout.edges.indices
            .filter { layout.edges[it].kind == EdgeKind.ASSOCIATION && layout.edges[it].source != layout.edges[it].target }
            .groupBy { setOf(layout.edges[it].source, layout.edges[it].target) }

        // Straighten the shortest first: they are the most likely to succeed, and longer lines then route around them.
        groups.values.sortedBy { idx -> layout.edges[idx.first()].let { distance(boxes.getValue(it.source).centre, boxes.getValue(it.target).centre) } }.forEach { indices ->
            tryStraighten(current, indices, boxes)?.let { current = it }
        }

        return current
    }

    private fun tryStraighten(layout: DiagramLayout, indices: List<Int>, boxes: Map<String, Box>): DiagramLayout? {
        val edges = layout.edges
        if (indices.all { edges[it].points.size <= 2 })
            return null

        // A consistent orientation for the group, so offsets go to the same side for every line in it.
        val (a, b) = edges[indices.first()].let { it.source to it.target }
        val lines = indices.mapIndexed { i, index ->
            val offset = (i - (indices.size - 1) / 2.0) * PARALLEL_SPACING
            val line = straightLine(boxes.getValue(a), boxes.getValue(b), offset) ?: return null
            // Keep each edge's own direction so its source and target labels stay at the right ends.
            index to if (edges[index].source == a) line else line.second to line.first
        }.toMap()

        val others = edges.indices.filter { it !in lines }
        val otherSegments = others.flatMap { layout.edgeSegments[it] }
        val otherLabels = others.flatMap { edges[it].labels }

        for ((index, line) in lines) {
            val edge = edges[index]
            if (line.length < MIN_LENGTH)
                return null
            if (boxes.any { (name, box) -> name != edge.source && name != edge.target && box.grow(CLEARANCE).intersects(line) })
                return null
            if (otherSegments.any { tooClose(line, it) })
                return null
            if (otherLabels.any { it.box().grow(LabelPlacer.GAP).intersects(line) })
                return null
        }

        val trial = lines.entries.fold(layout) { acc, (index, line) ->
            acc.withEdge(index) { it.copy(points = listOf(line.first, line.second), labels = emptyList()) }
        }
        if (trial.metrics.crossings > layout.metrics.crossings)
            return null

        // Place the labels of each straightened edge, avoiding everything already placed.
        var placed = trial
        for ((index, line) in lines) {
            val labels = edges[index].labels.map { label ->
                val end = if (label.atSource) line.first else line.second
                val towards = if (label.atSource) line.second else line.first
                placeLabel(label, end, towards, placed) ?: return null
            }
            placed = placed.withEdge(index) { it.copy(labels = it.labels + labels) }
        }

        return placed
    }

    /**
     * The line joining the centres of [from] and [to], shifted sideways by [offset], clipped to the borders of the two
     * boxes. Null if the shifted line no longer passes through both boxes, or the boxes overlap along it.
     */
    private fun straightLine(from: Box, to: Box, offset: Double): Segment? {
        val dx = to.cx - from.cx
        val dy = to.cy - from.cy
        val length = hypot(dx, dy)
        if (length < 1e-6)
            return null

        val nx = -dy / length * offset
        val ny = dx / length * offset
        val start = Point(from.cx + nx, from.cy + ny)
        val end = Point(to.cx + nx, to.cy + ny)
        if (!from.contains(start) || !to.contains(end))
            return null

        val exit = from.exitT(start, end) ?: return null
        val entry = 1 - (to.exitT(end, start) ?: return null)
        if (exit >= entry)
            return null

        fun at(t: Double) = Point(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t)
        return at(exit) to at(entry)
    }

    /** The best position for [label] at [end], but only if it conflicts with nothing. */
    private fun placeLabel(label: PositionedLabel, end: Point, towards: Point, layout: DiagramLayout): PositionedLabel? =
        LabelPlacer.best(label, end, towards, layout, others = layout.edges.flatMap { it.labels })
            .takeIf { it.conflicts.total == 0 }?.label

    /** True if [line] runs within [CLEARANCE] of [other] anywhere except where they share an end. */
    private fun tooClose(line: Segment, other: Segment): Boolean {
        if (properlyCross(line, other))
            return false // A clean crossing is accounted for by the crossing count, not a near miss.
        val steps = (line.length / 4).toInt().coerceAtLeast(1)
        return (1 until steps).any { i ->
            val t = i.toDouble() / steps
            val p = Point(line.first.x + (line.second.x - line.first.x) * t, line.first.y + (line.second.y - line.first.y) * t)
            distanceToSegment(p, other) < CLEARANCE &&
                distance(p, line.first) > CLEARANCE && distance(p, line.second) > CLEARANCE
        }
    }

}
