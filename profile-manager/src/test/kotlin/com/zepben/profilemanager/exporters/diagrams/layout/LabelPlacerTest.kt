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
import com.zepben.profilemanager.exporters.diagrams.render.SvgRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LabelPlacerTest {

    private fun node(name: String, x: Double, y: Double) =
        PositionedNode(SizedNode(DiagramNode(name, "/$name", emptyList(), false, emptyList()), 100.0, 40.0, 40.0, 16.0), x, y)

    // A vertical association from the bottom of A down to the top of B, labelled at A's end to the right of the line.
    private val label = PositionedLabel("+bs 0..*", 53.0, 43.0, 60.0, 13.0, atSource = true)
    private val association = PositionedEdge(
        EdgeKind.ASSOCIATION, "A", "B", listOf(Point(50.0, 40.0), Point(50.0, 200.0)), listOf(label),
    )

    private fun layout(vararg edges: PositionedEdge) =
        DiagramLayout(
            listOf(node("A", 0.0, 0.0), node("B", 0.0, 200.0), node("C", 300.0, 100.0)),
            edges.toList(),
            420.0,
            260.0
        )

    @Test
    fun `labels clear of lines are left alone`() {
        assertEquals(label, LabelPlacer.improve(layout(association)).edges.single().labels.single())
    }

    @Test
    fun `labels hug their line equally on either side`() {
        // As ELK leaves them: the right hand label 5 from the line, the left hand one further away.
        val right = label.copy(x = 55.0)
        val left = label.copy(x = 50.0 - 12.0 - label.width, atSource = false, y = 180.0)
        val edge = association.copy(labels = listOf(right, left))

        val aligned = LabelPlacer.alignToLines(layout(edge)).edges.single().labels
        val (r, l) = aligned

        assertEquals(5.0, r.x - 50.0, 1e-9)
        assertEquals(false, r.alignEnd)
        assertEquals(5.0, 50.0 - (l.x + l.width), 1e-9)
        assertEquals(true, l.alignEnd, "Left hand labels are right aligned, so the text ends next to the line")

        val svg = SvgRenderer("DejaVu Sans").render(layout(edge.copy(labels = aligned)))
        assertTrue(svg.contains("""x="${(l.x + l.width).toInt()}" """) && svg.contains("text-anchor=\"end\">${l.text}"), svg)
    }

    @Test
    fun `labels a line runs through are moved clear of it, near the same end`() {
        // An unrelated line passing right through the label.
        val crossing = PositionedEdge(
            EdgeKind.ASSOCIATION,
            "A",
            "C",
            listOf(Point(80.0, 30.0), Point(80.0, 100.0), Point(300.0, 100.0)),
            emptyList()
        )
        val before = layout(association, crossing)
        assertTrue(before.metrics.labelLineHits > 0)

        val after = LabelPlacer.improve(before)
        val moved = after.edges.first().labels.single()

        assertEquals(0, after.metrics.labelLineHits, "Moved to $moved")
        assertTrue(moved.y < 100, "Still near A's end of the line: $moved")
    }

}
