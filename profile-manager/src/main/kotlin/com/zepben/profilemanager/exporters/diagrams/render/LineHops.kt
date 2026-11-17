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
     */
    fun path(points: List<Point>, hops: List<List<Hop>>): String = buildString {
        append("M").append(svgNumber(points.first().x)).append(",").append(svgNumber(points.first().y))
        points.zipWithNext().forEachIndexed { s, (a, b) ->
            val length = hypot(b.x - a.x, b.y - a.y)
            val dx = (b.x - a.x) / length
            val dy = (b.y - a.y) / length
            // An arc with the sweep flag set bulges to the side (dy, -dx) of the direction of travel: pick it so hops
            // bulge upwards, or to the right on vertical lines.
            val sweep = if (abs(dx) > 1e-9) (if (dx > 0) 1 else 0) else (if (dy > 0) 1 else 0)
            hops.getOrNull(s).orEmpty().forEach { hop ->
                val radius = (hop.to - hop.from) / 2
                append(" L").append(svgNumber(a.x + dx * hop.from)).append(",").append(svgNumber(a.y + dy * hop.from))
                append(" A").append(svgNumber(radius)).append(",").append(svgNumber(radius)).append(" 0 0 ").append(sweep).append(" ")
                append(svgNumber(a.x + dx * hop.to)).append(",").append(svgNumber(a.y + dy * hop.to))
            }
            append(" L").append(svgNumber(b.x)).append(",").append(svgNumber(b.y))
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
