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
import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Link
import kotlin.test.Test
import kotlin.test.assertEquals

class BestLayoutTest {

    private val sizer = BoxSizer(TextMeasurer("DejaVu Sans"))

    private val switch = Class(name = "Switch")
    private val leaves = listOf("Jumper", "Disconnector", "Fuse").map { Class(name = it, ancestors = mutableListOf("Switch")) }
    private val protectedSwitch = Class(
        name = "ProtectedSwitch",
        ancestors = mutableListOf("Switch"),
        associations = mutableListOf(Link("ProtectedSwitch", "Switch", "0..1", "protected", null, "0..1", "switch", null)),
    )

    private val model = DiagramModelBuilder(classIndexOf(switch, protectedSwitch, *leaves.toTypedArray()))
        .build(DiagramDefinition("Test", classes = listOf("Switch", "ProtectedSwitch", "Jumper", "Disconnector", "Fuse").map(::DiagramClass)))

    @Test
    fun `best layout picks the lowest scoring candidate, penalising size`() {
        val candidates = listOf(
            LayoutConfig("spread", stackLeavesFrom = null),
            LayoutConfig("stacked", stackLeavesFrom = 2)
        )
        val best = BestLayout(sizer, candidates).layout(model)

        val layouts = candidates.map { tidy(ElkLayout(sizer, it).layout(model)) }
        val smallest = layouts.minOf { it.width * it.height }
        val expected = layouts.minBy { it.metrics.score + BestLayout.SIZE_PENALTY * (it.width * it.height / smallest - 1) }
        assertEquals(expected.width * expected.height, best.width * best.height, 1e-6)
        assertEquals(expected.metrics.score, best.metrics.score, 1e-9)
    }

}
