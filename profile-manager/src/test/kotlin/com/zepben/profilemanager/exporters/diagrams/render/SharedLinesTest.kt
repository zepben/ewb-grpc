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
import com.zepben.profilemanager.exporters.diagrams.render.SharedLines.Span
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedLinesTest {

    private fun generalisation(vararg points: Point) =
        PositionedEdge(EdgeKind.GENERALISATION, "Sub", "Sup", points.toList(), emptyList())

    private fun association(vararg points: Point) =
        PositionedEdge(EdgeKind.ASSOCIATION, "A", "B", points.toList(), emptyList())

    private fun path(edge: PositionedEdge, drawing: SharedLines.Drawing) =
        LineHops.path(edge.points, edge.points.zipWithNext().map { emptyList() }, drawing.visible)

    // Two subclasses on a bus line at x = 10, merging into a trunk up to their superclass at (100, 0).
    private val first = generalisation(Point(20.0, 100.0), Point(10.0, 100.0), Point(10.0, 50.0), Point(100.0, 50.0), Point(100.0, 0.0))
    private val second = generalisation(Point(20.0, 150.0), Point(10.0, 150.0), Point(10.0, 50.0), Point(100.0, 50.0), Point(100.0, 0.0))

    @Test
    fun `the first edge is drawn in full`() {
        val drawing = SharedLines.of(listOf(first, second))[0]

        assertTrue(drawing.marker)
        assertEquals("M20,100 L10,100 L10,50 L100,50 L100,0", path(first, drawing))
    }

    @Test
    fun `a later edge only draws what the earlier ones have not`() {
        val drawing = SharedLines.of(listOf(first, second))[1]

        assertFalse(drawing.marker, "The arrowhead is already drawn by the first edge")
        assertEquals(listOf(listOf(Span(0.0, 10.0)), listOf(Span(0.0, 50.0)), emptyList(), emptyList()), drawing.visible)
        assertEquals("M20,150 L10,150 L10,100", path(second, drawing))
    }

    @Test
    fun `an edge entirely drawn by an earlier one is empty`() {
        assertTrue(SharedLines.of(listOf(first, first))[1].isEmpty)
    }

    @Test
    fun `a gap in the middle of a segment splits the path`() {
        val long = association(Point(0.0, 0.0), Point(100.0, 0.0))
        val drawings = SharedLines.of(listOf(association(Point(30.0, 0.0), Point(60.0, 0.0)), long))

        assertEquals("M0,0 L30,0 M60,0 L100,0", path(long, drawings[1]))
    }

    @Test
    fun `parallel lines apart from each other are both drawn`() {
        val other = generalisation(Point(20.0, 100.0), Point(20.0, 0.0))
        val drawing = SharedLines.of(listOf(generalisation(Point(10.0, 100.0), Point(10.0, 0.0)), other))[1]

        assertTrue(drawing.marker)
        assertEquals("M20,100 L20,0", path(other, drawing))
    }

    @Test
    fun `an arrowhead is kept when an earlier line passes through the end without one`() {
        val passing = association(Point(100.0, 100.0), Point(100.0, -100.0))
        val drawing = SharedLines.of(listOf(passing, generalisation(Point(100.0, 50.0), Point(100.0, 0.0))))[1]

        assertTrue(drawing.marker)
        assertEquals(listOf(listOf(Span(0.0, 50.0))), drawing.visible)
    }

    @Test
    fun `hops are only drawn on visible spans`() {
        val line = association(Point(0.0, 0.0), Point(100.0, 0.0))
        val hops = listOf(listOf(LineHops.Hop(20.0, 29.0), LineHops.Hop(70.0, 79.0)))

        assertEquals("M0,0 L20,0 A4.5,4.5 0 0 1 29,0 L50,0", LineHops.path(line.points, hops, listOf(listOf(Span(0.0, 50.0)))))
    }

}
