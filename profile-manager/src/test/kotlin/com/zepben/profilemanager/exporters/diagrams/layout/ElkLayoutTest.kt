/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import com.zepben.profilemanager.exporters.classIndexOf
import com.zepben.profilemanager.exporters.diagrams.model.DiagramClass
import com.zepben.profilemanager.exporters.diagrams.model.DiagramDefinition
import com.zepben.profilemanager.exporters.diagrams.model.DiagramModelBuilder
import com.zepben.profilemanager.exporters.diagrams.model.DiagramNode
import com.zepben.profilemanager.exporters.diagrams.GENERALISATION_ARROW
import com.zepben.profilemanager.exporters.diagrams.MIN_ARROW_STEM
import com.zepben.profilemanager.exporters.diagrams.STEREOTYPE_FONT_SIZE
import com.zepben.profilemanager.exporters.diagrams.model.EdgeKind
import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Link
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElkLayoutTest {

    private val sizer = BoxSizer(TextMeasurer("DejaVu Sans"))

    private val switch = Class(name = "Switch")
    private val leaves = listOf("Jumper", "Disconnector", "Fuse").map { Class(name = it, ancestors = mutableListOf("Switch")) }
    private val protectedSwitch = Class(
        name = "ProtectedSwitch",
        ancestors = mutableListOf("Switch"),
        associations = mutableListOf(Link("ProtectedSwitch", "Switch", "0..1", "protected", null, "0..1", "switch", null)),
    )

    private val model = DiagramModelBuilder(classIndexOf(switch, protectedSwitch, *leaves.toTypedArray()))
        .build(
            DiagramDefinition(
                "Test",
                classes = listOf("Switch", "ProtectedSwitch", "Jumper", "Disconnector", "Fuse").map(::DiagramClass)
            )
        )

    @Test
    fun `leaf subclasses are stacked under their superclass`() {
        val layout = ElkLayout(sizer, LayoutConfig(stackLeavesFrom = 3)).layout(model)
        val nodes = layout.nodes.associateBy { it.name }

        // Leaves share a column, one above the other, in definition order.
        val stack = listOf("Jumper", "Disconnector", "Fuse").map { nodes.getValue(it) }
        assertEquals(1, stack.map { it.x }.distinct().size)
        stack.zipWithNext().forEach { (above, below) -> assertTrue(below.y >= above.y + above.height) }

        // ProtectedSwitch has an association too, so isn't a leaf and isn't stacked.
        assertTrue(nodes.getValue("ProtectedSwitch").x != stack.first().x)

        // Every leaf still has a generalisation ending at the bottom of Switch.
        val parent = nodes.getValue("Switch")
        layout.edges.filter { it.kind == EdgeKind.GENERALISATION && it.source in setOf("Jumper", "Disconnector", "Fuse") }.let { edges ->
            assertEquals(3, edges.size)
            edges.forEach { assertEquals(parent.y + parent.height, it.points.last().y, 0.5) }
        }
        assertEquals(0, layout.metrics.crossings)
        assertEquals(0, layout.metrics.nodeHits)
    }

    @Test
    fun `leaves are not stacked below the threshold`() {
        val layout = ElkLayout(sizer, LayoutConfig(stackLeavesFrom = 4)).layout(model)
        val xs = layout.nodes.filter { it.name in setOf("Jumper", "Disconnector", "Fuse") }.map { it.x }

        assertEquals(3, xs.distinct().size, "Unstacked leaves sit side by side, not in a column")
    }

    @Test
    fun `generalisation triangles have a stem before the line turns`() {
        // Unstacked, the subclasses join Switch through a shared horizontal line, which used to turn right under the triangle.
        listOf(LayoutConfig(stackLeavesFrom = null), LayoutConfig(stackLeavesFrom = 3)).forEach { config ->
            val layout = ElkLayout(sizer, config).layout(model)
            layout.edges.filter { it.kind == EdgeKind.GENERALISATION }.forEach { edge ->
                val (from, to) = edge.points.takeLast(2)
                assertEquals(from.x, to.x, 0.01, "Generalisations enter the superclass vertically")
                assertTrue(
                    from.y - to.y >= GENERALISATION_ARROW + MIN_ARROW_STEM - 0.01,
                    "Stem of ${edge.source} -> ${edge.target} is only ${from.y - to.y} with ${config.stackLeavesFrom}",
                )
            }
        }
    }

    @Test
    fun `wholes are drawn above their parts`() {
        // Each link is recorded from the part's side, but the result mustn't depend on which side or name order.
        val region = Class(name = "Region")
        val subRegion = Class(name = "ASubRegion", associations = mutableListOf(Link("ASubRegion", "Region", "0..*", "subRegions", null, "0..1", "region", null)))
        val site = Class(name = "Site", associations = mutableListOf(Link("Site", "ASubRegion", "0..*", "sites", null, "1", "subRegion", null)))
        val model = DiagramModelBuilder(classIndexOf(region, subRegion, site)).build(
            DiagramDefinition(
                "Test",
                classes = listOf("Site", "ASubRegion", "Region").map(::DiagramClass)
            )
        )

        BestLayout.CANDIDATES.forEach { config ->
            val y = ElkLayout(sizer, config).layout(model).nodes.associate { it.name to it.y }
            assertTrue(y.getValue("Region") < y.getValue("ASubRegion") && y.getValue("ASubRegion") < y.getValue("Site"), "${config.name}: $y")
        }
    }

    @Test
    fun `one to one associations do not add a layer`() {
        // Top <- Middle <- Bottom is an inheritance chain, and Side is a subclass of Top. A one to one association between
        // Bottom and Side should put Side above Bottom (which already has two classes above it), not below it.
        val top = Class(name = "Top")
        val middle = Class(name = "Middle", ancestors = mutableListOf("Top"))
        val bottom = Class(name = "Bottom", ancestors = mutableListOf("Middle"))
        val side = Class(name = "Side", ancestors = mutableListOf("Top"), associations = mutableListOf(Link("Side", "Bottom", "0..1", "side", null, "0..1", "bottom", null)))
        val model = DiagramModelBuilder(classIndexOf(top, middle, bottom, side)).build(
            DiagramDefinition(
                "Test",
                classes = listOf("Top", "Middle", "Bottom", "Side").map(::DiagramClass)
            )
        )

        val nodes = ElkLayout(sizer, LayoutConfig()).layout(model).nodes.associateBy { it.name }
        val bottomNode = nodes.getValue("Bottom")
        assertTrue(nodes.getValue("Side").y < bottomNode.y, "Side should be above Bottom")
        assertTrue(nodes.values.none { it.y > bottomNode.y }, "Nothing should be below Bottom")
    }

    @Test
    fun `boxes make room for the name of an absent superclass`() {
        val plain = DiagramNode("A", "/A", emptyList(), false, emptyList())
        val withParent = plain.copy(absentParents = listOf("AVeryLongSuperclassNameIndeed"))

        val a = sizer.size(plain)
        val b = sizer.size(withParent)
        assertTrue(b.height > a.height, "Extra line for the superclass name")
        assertTrue(b.width >= TextMeasurer("DejaVu Sans").width("AVeryLongSuperclassNameIndeed", STEREOTYPE_FONT_SIZE, italic = true))
    }

}
