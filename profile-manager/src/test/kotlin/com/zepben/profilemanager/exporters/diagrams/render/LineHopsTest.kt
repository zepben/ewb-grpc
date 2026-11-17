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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LineHopsTest {

    private fun edge(vararg points: Point) =
        PositionedEdge(EdgeKind.ASSOCIATION, "A", "B", points.toList(), emptyList())

    private val vertical = edge(Point(50.0, 0.0), Point(50.0, 100.0))
    private val horizontal = edge(Point(0.0, 50.0), Point(100.0, 50.0))

    @Test
    fun `the horizontal line hops over the vertical one`() {
        val hops = LineHops.of(listOf(vertical, horizontal))

        assertTrue(hops[0].single().isEmpty(), "The vertical line is drawn straight")
        val hop = hops[1].single().single()
        assertEquals(50.0 - LineHops.RADIUS, hop.from, 1e-9)
        assertEquals(50.0 + LineHops.RADIUS, hop.to, 1e-9)

        // Drawn as a line to the hop, an upward arc over the crossing, then on to the end.
        assertEquals("M0,50 L45.5,50 A4.5,4.5 0 0 1 54.5,50 L100,50", LineHops.path(horizontal.points, hops[1]))
    }

    @Test
    fun `lines that only touch, or cross right at an end, do not hop`() {
        val touching = edge(Point(50.0, 50.0), Point(100.0, 50.0)) // Starts on the vertical line.
        val nearEnd = edge(Point(48.0, 10.0), Point(150.0, 10.0)) // Crosses 2px from its start.

        assertTrue(LineHops.of(listOf(vertical, touching)).flatten().flatten().isEmpty())
        assertTrue(LineHops.of(listOf(vertical, nearEnd)).flatten().flatten().isEmpty())
    }

    @Test
    fun `crossings close together share one hop`() {
        val second = edge(Point(55.0, 0.0), Point(55.0, 100.0))

        val hop = LineHops.of(listOf(vertical, second, horizontal))[2].single().single()
        assertEquals(50.0 - LineHops.RADIUS, hop.from, 1e-9)
        assertEquals(55.0 + LineHops.RADIUS, hop.to, 1e-9)
    }

}
