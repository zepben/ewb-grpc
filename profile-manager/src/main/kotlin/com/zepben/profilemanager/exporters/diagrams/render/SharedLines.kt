/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.render

import com.zepben.profilemanager.exporters.diagrams.layout.*
import com.zepben.profilemanager.exporters.diagrams.model.EdgeKind
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Edges often share part of their route, most visibly the generalisations from several subclasses merging into one
 * line up to their superclass. Drawing each edge in full stacks the shared lines (and arrowheads) on top of each other,
 * which anti-aliasing turns into thicker, darker lines. Instead, each edge only draws the parts of its route that no
 * edge drawn before it has, and each arrowhead is only drawn once.
 */
object SharedLines {

    /** The stretch of a segment between distances [from] and [to] along it. */
    data class Span(val from: Double, val to: Double)

    /**
     * How to draw an edge.
     *
     * @property visible The spans of each segment of the edge to draw, by segment.
     * @property marker Whether to draw the edge's arrowhead (if it has one).
     */
    data class Drawing(val visible: List<List<Span>>, val marker: Boolean) {
        val isEmpty get() = visible.all { it.isEmpty() }
    }

    /** Lines closer than this (and parallel) are treated as the same line. */
    private const val TOLERANCE = 0.5

    /** How to draw each edge of [edges], drawn in order. */
    fun of(edges: List<PositionedEdge>): List<Drawing> {
        val segments = edges.map { it.points.zipWithNext() }
        val markers = mutableListOf<Pair<Point, Point>>()

        return edges.mapIndexed { e, edge ->
            val earlier = segments.subList(0, e).flatten()
            val visible = segments[e].map { visibleSpans(it, earlier) }.toMutableList()

            val end = segments[e].lastOrNull()
            val marker = edge.kind == EdgeKind.GENERALISATION && end != null && end.length > 0 &&
                markers.none { (at, direction) -> same(at, end.second) && sameDirection(direction, end) }

            if (marker) {
                markers += end.second to Point(end.second.x - end.first.x, end.second.y - end.first.y)
                // The arrowhead goes on the end of the path, so the end must be drawn. It's only hidden when an earlier
                // edge passes through this one's end without having an arrowhead there itself, so is rarely needed.
                val length = end.length
                if (visible.last().lastOrNull()?.let { abs(it.to - length) < TOLERANCE } != true)
                    visible[visible.size - 1] = listOf(Span(0.0, length))
            }

            Drawing(visible, marker)
        }
    }

    /** The spans of [segment] not covered by any of [drawn]. */
    private fun visibleSpans(segment: Segment, drawn: List<Segment>): List<Span> {
        val length = segment.length
        if (length < TOLERANCE)
            return emptyList()

        val covered = drawn.mapNotNull { coverage(segment, length, it) }.sortedBy { it.from }
        val visible = mutableListOf<Span>()
        var at = 0.0
        covered.forEach { span ->
            if (span.from - at > TOLERANCE)
                visible += Span(at, span.from)
            at = max(at, span.to)
        }
        if (length - at > TOLERANCE)
            visible += Span(at, length)
        return visible
    }

    /** The span of [segment] (of [length]) that [other] runs along, if any. */
    private fun coverage(segment: Segment, length: Double, other: Segment): Span? {
        val (a, b) = segment
        val ux = (b.x - a.x) / length
        val uy = (b.y - a.y) / length

        // Both ends of the other segment must be on the line through this one.
        fun offset(p: Point) = abs((p.x - a.x) * uy - (p.y - a.y) * ux)
        if (offset(other.first) > TOLERANCE || offset(other.second) > TOLERANCE)
            return null

        fun along(p: Point) = (p.x - a.x) * ux + (p.y - a.y) * uy
        val from = max(0.0, min(along(other.first), along(other.second)))
        val to = min(length, max(along(other.first), along(other.second)))
        return if (to - from > TOLERANCE) Span(from, to) else null
    }

    private fun same(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y) < TOLERANCE

    private fun sameDirection(direction: Point, s: Segment): Boolean {
        val dx = s.second.x - s.first.x
        val dy = s.second.y - s.first.y
        val cos = (direction.x * dx + direction.y * dy) / (hypot(direction.x, direction.y) * hypot(dx, dy))
        return cos > 0.999
    }

}
