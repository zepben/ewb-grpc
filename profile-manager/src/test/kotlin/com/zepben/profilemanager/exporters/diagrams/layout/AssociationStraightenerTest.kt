/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import com.zepben.profilemanager.exporters.diagrams.model.DiagramNode
import com.zepben.profilemanager.exporters.diagrams.model.EdgeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssociationStraightenerTest {

    private fun node(name: String, x: Double, y: Double, w: Double = 100.0, h: Double = 40.0) =
        PositionedNode(SizedNode(DiagramNode(name, "/$name", emptyList(), false, emptyList()), w, h, 20.0, 16.0), x, y)

    // Positioned where ELK would put them, next to the ends of the orthogonal route.
    private fun label(text: String, atSource: Boolean) =
        PositionedLabel(text, if (atSource) 54.0 else 354.0, if (atSource) 44.0 else 183.0, 60.0, 13.0, atSource)

    /** An orthogonal route from the bottom of A (at 0,0) to the top of B (at 300,200), as ELK might produce. */
    private fun routedEdge() = PositionedEdge(
        EdgeKind.ASSOCIATION, "A", "B",
        listOf(Point(50.0, 40.0), Point(50.0, 120.0), Point(350.0, 120.0), Point(350.0, 200.0)),
        listOf(label("+as 0..*", atSource = true), label("+b 0..1", atSource = false)),
    )

    @Test
    fun `a clear route is replaced with a straight line between the box borders`() {
        val layout =
            DiagramLayout(listOf(node("A", 0.0, 0.0), node("B", 300.0, 200.0)), listOf(routedEdge()), 420.0, 260.0)

        val edge = AssociationStraightener.straighten(layout).edges.single()

        assertEquals(2, edge.points.size)
        val (start, end) = edge.points
        assertTrue(start.y == 40.0 || start.x == 100.0, "Starts on A's border: $start")
        assertTrue(end.y == 200.0 || end.x == 300.0, "Ends on B's border: $end")

        // Labels stay at their own ends, outside both boxes.
        val (source, target) = edge.labels.partition { it.atSource }.let { it.first.single() to it.second.single() }
        assertTrue(source.y < 120 && target.y > 120, "Source label near A, target label near B: $source $target")
        listOf(source, target).forEach { l ->
            layout.nodes.forEach { n ->
                val overlaps = l.x < n.x + n.width && n.x < l.x + l.width && l.y < n.y + n.height && n.y < l.y + l.height
                assertTrue(!overlaps, "${l.text} overlaps ${n.name}")
            }
        }
    }

    @Test
    fun `routes stay orthogonal when a straight line would pass through another class`() {
        val blocker = node("C", 150.0, 90.0, w = 80.0, h = 40.0)
        val layout = DiagramLayout(
            listOf(node("A", 0.0, 0.0), node("B", 300.0, 200.0), blocker),
            listOf(routedEdge()),
            420.0,
            260.0
        )

        assertEquals(routedEdge().points, AssociationStraightener.straighten(layout).edges.single().points)
    }

    @Test
    fun `parallel associations are straightened as separate lines`() {
        val edges = listOf(routedEdge(), routedEdge().copy(labels = listOf(label("+cs 0..*", true), label("+d 0..1", false))))
        val layout = DiagramLayout(
            listOf(node("A", 0.0, 0.0, w = 160.0), node("B", 300.0, 200.0, w = 160.0)),
            edges,
            520.0,
            300.0
        )

        val straightened = AssociationStraightener.straighten(layout).edges
        assertTrue(straightened.all { it.points.size == 2 }, "Both straightened")
        assertTrue(straightened[0].points != straightened[1].points, "Drawn as two distinct lines")
    }

}
