/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class Point(val x: Double, val y: Double)

/** A straight line between two points. */
typealias Segment = Pair<Point, Point>

/** An axis aligned rectangle, with the geometry tests used when scoring and tidying layouts. */
internal data class Box(val x: Double, val y: Double, val w: Double, val h: Double) {

    val cx get() = x + w / 2
    val cy get() = y + h / 2
    val centre get() = Point(cx, cy)

    /** This box grown by [by] on every side, or shrunk for a negative [by]. */
    fun grow(by: Double) = Box(x - by, y - by, w + 2 * by, h + 2 * by)

    fun contains(p: Point) = p.x > x && p.x < x + w && p.y > y && p.y < y + h

    fun overlaps(o: Box) = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h

    /** True if any part of [s] lies inside the box (Liang-Barsky clipping). */
    fun intersects(s: Segment): Boolean {
        val (a, b) = s
        var t0 = 0.0
        var t1 = 1.0
        val dx = b.x - a.x
        val dy = b.y - a.y
        for ((p, q) in listOf(-dx to a.x - x, dx to x + w - a.x, -dy to a.y - y, dy to y + h - a.y)) {
            if (abs(p) < 1e-12) {
                if (q < 0) return false
            } else {
                val r = q / p
                if (p < 0) t0 = max(t0, r) else t1 = min(t1, r)
                if (t0 > t1) return false
            }
        }
        return true
    }

    /**
     * True if either end of [s] is strictly inside the box, or [s] properly crosses one of its sides. Unlike
     * [intersects], a segment running exactly along a side doesn't count.
     */
    fun crossedBy(s: Segment): Boolean {
        if (contains(s.first) || contains(s.second))
            return true
        val corners = listOf(Point(x, y), Point(x + w, y), Point(x + w, y + h), Point(x, y + h))
        return corners.zip(corners.drop(1) + corners.first()).any { properlyCross(s, it) }
    }

    /** The fraction along [from] -> [to] at which the segment leaves this box ([from] must be inside). */
    fun exitT(from: Point, to: Point): Double? {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val ts = listOfNotNull(
            if (dx > 0) (x + w - from.x) / dx else if (dx < 0) (x - from.x) / dx else null,
            if (dy > 0) (y + h - from.y) / dy else if (dy < 0) (y - from.y) / dy else null,
        )
        return ts.minOrNull()?.takeIf { it in 0.0..1.0 }
    }

}

internal fun PositionedLabel.box() = Box(x, y, width, height)

internal fun PositionedNode.box() = Box(x, y, width, height)

internal fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

internal val Segment.length get() = distance(first, second)

internal fun distanceToSegment(p: Point, s: Segment): Double {
    val (a, b) = s
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
    return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
}

/** True if the segments cross at a single point that isn't an end of either (touching and overlapping don't count). */
internal fun properlyCross(s1: Segment, s2: Segment): Boolean {
    val (a, b) = s1
    val (c, d) = s2
    if (max(a.x, b.x) < min(c.x, d.x) || max(c.x, d.x) < min(a.x, b.x) || max(a.y, b.y) < min(c.y, d.y) || max(c.y, d.y) < min(a.y, b.y))
        return false
    val o1 = orientation(a, b, c)
    val o2 = orientation(a, b, d)
    val o3 = orientation(c, d, a)
    val o4 = orientation(c, d, b)
    return o1 * o2 < 0 && o3 * o4 < 0
}

/** Segment [segmentA] of edge [edgeA] properly crosses segment [segmentB] of a later edge [edgeB]. */
internal data class Crossing(val edgeA: Int, val segmentA: Int, val edgeB: Int, val segmentB: Int)

/** Every place where two different edges, each given as its line segments, cross. */
internal fun crossings(edges: List<List<Segment>>): List<Crossing> = buildList {
    for (i in edges.indices)
        for (j in i + 1 until edges.size)
            edges[i].forEachIndexed { si, a ->
                edges[j].forEachIndexed { sj, b -> if (properlyCross(a, b)) add(Crossing(i, si, j, sj)) }
            }
}

/** Where the lines through [s1] and [s2] meet. Only meaningful if they aren't parallel. */
internal fun intersection(s1: Segment, s2: Segment): Point {
    val (a, b) = s1
    val (c, d) = s2
    val denominator = (b.x - a.x) * (d.y - c.y) - (b.y - a.y) * (d.x - c.x)
    val t = ((c.x - a.x) * (d.y - c.y) - (c.y - a.y) * (d.x - c.x)) / denominator
    return Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}

private fun orientation(a: Point, b: Point, c: Point): Int {
    val v = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    return when {
        v > 1e-6 -> 1
        v < -1e-6 -> -1
        else -> 0
    }
}

