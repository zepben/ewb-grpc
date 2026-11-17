/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.render

import com.zepben.profilemanager.exporters.diagrams.layout.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Where lines cross, one of them "hops" over the other with a small semicircle, so it's clear the lines aren't joined.
 * The flatter of the two lines hops (as in Enterprise Architect), so horizontal lines arc over vertical ones. Hops bulge
 * upwards on horizontal lines and to the right on vertical ones. Crossings close together on the same line share one
 * wider hop, and crossings too close to the end of a line are left as they are.
 */
object LineHops {

    const val RADIUS = 4.5

    /**
     * A hop over the stretch of a segment between distances [from] and [to] along it.
     */
    data class Hop(val from: Double, val to: Double)

    /**
     * The hops for every segment of every edge, indexed by edge then segment.
     */
    fun of(edges: List<PositionedEdge>): List<List<List<Hop>>> {
        val segments = edges.map { it.points.zipWithNext() }
        val crossings = segments.map { edge -> edge.map { mutableListOf<Double>() } }

        crossings(segments).forEach { (i, si, j, sj) ->
            val a = segments[i][si]
            val b = segments[j][sj]
            // The flatter segment hops; if equally flat, the one drawn later does.
            if (flatness(a) > flatness(b))
                crossings[i][si] += distance(a.first, intersection(a, b))
            else
                crossings[j][sj] += distance(b.first, intersection(b, a))
        }

        return segments.mapIndexed { e, edge ->
            edge.mapIndexed { s, segment -> hops(crossings[e][s].sorted(), segment.length) }
        }
    }

    /**
     * The SVG path data for a line through [points], hopping over the crossings in [hops] (one list per segment).
     *
     * Only the [visible] spans of each segment are drawn (by default, all of them), so the path can be in pieces. A hop
     * that doesn't fit within a visible span is left out.
     */
    fun path(
        points: List<Point>,
        hops: List<List<Hop>>,
        visible: List<List<SharedLines.Span>> = points.zipWithNext().map { listOf(SharedLines.Span(0.0, it.length)) },
    ): String = buildString {
        var pen: Point? = null
        // Start a new piece of the line at [p], unless it carries on from (or is a tiny jog from) the last piece.
        fun move(p: Point) {
            val gap = pen?.let { distance(it, p) }
            when {
                gap == null -> append("M")
                gap > 1.0 -> append(" M")
                gap > 0.05 -> append(" L")
                else -> return
            }
            append(svgNumber(p.x)).append(",").append(svgNumber(p.y))
        }

        points.zipWithNext().forEachIndexed { s, (a, b) ->
            val length = hypot(b.x - a.x, b.y - a.y)
            if (length == 0.0)
                return@forEachIndexed
            val dx = (b.x - a.x) / length
            val dy = (b.y - a.y) / length
            fun at(d: Double) = Point(a.x + dx * d, a.y + dy * d)
            // An arc with the sweep flag set bulges to the side (dy, -dx) of the direction of travel: pick it so hops
            // bulge upwards, or to the right on vertical lines.
            val sweep = if (abs(dx) > 1e-9) (if (dx > 0) 1 else 0) else (if (dy > 0) 1 else 0)
            visible.getOrNull(s).orEmpty().forEach { span ->
                move(at(span.from))
                hops.getOrNull(s).orEmpty().filter { it.from >= span.from && it.to <= span.to }.forEach { hop ->
                    val radius = (hop.to - hop.from) / 2
                    val (from, to) = at(hop.from) to at(hop.to)
                    append(" L").append(svgNumber(from.x)).append(",").append(svgNumber(from.y))
                    append(" A").append(svgNumber(radius)).append(",").append(svgNumber(radius)).append(" 0 0 ").append(sweep).append(" ")
                    append(svgNumber(to.x)).append(",").append(svgNumber(to.y))
                }
                // End exactly on the segment's end where it's drawn to there, rather than a point computed from it.
                val end = if (span.to >= length - 1e-9) b else at(span.to)
                append(" L").append(svgNumber(end.x)).append(",").append(svgNumber(end.y))
                pen = end
            }
        }
    }

    /** Hops for crossings at distances [crossings] along a segment of [length], merging those too close together. */
    private fun hops(crossings: List<Double>, length: Double): List<Hop> {
        val hops = mutableListOf<Hop>()
        crossings
            .filter { it > RADIUS + 1 && it < length - RADIUS - 1 }
            .forEach { at ->
                val last = hops.lastOrNull()
                if (last != null && at - RADIUS < last.to + 2)
                    hops[hops.size - 1] = last.copy(to = at + RADIUS)
                else
                    hops += Hop(at - RADIUS, at + RADIUS)
            }
        return hops
    }

    /** How horizontal a segment is, from 0 (vertical) to 1 (horizontal). */
    private fun flatness(s: Segment): Double = abs(s.second.x - s.first.x) / s.length.coerceAtLeast(1e-9)

}

/** A coordinate for SVG output: one decimal place, without a trailing `.0`. */
internal fun svgNumber(value: Double): String = String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
