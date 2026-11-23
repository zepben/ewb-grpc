/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.model

import com.zepben.profilemanager.exporters.classIndexOf
import com.zepben.profilemanager.models.Attribute
import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Link
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramModelTest {

    private val terminalToNode = Link(
        source = "Terminal", target = "ConnectivityNode",
        sourceCardinality = "0..*", sourceName = "terminals",
        targetCardinality = "0..1", targetName = "connectivityNode",
    )

    private val terminal = Class(
        name = "Terminal",
        attributes = mutableListOf(Attribute("phases", "PhaseCode"), Attribute("sequenceNumber", "Integer", nullable = true)),
        ancestors = mutableListOf("ACDCTerminal"),
        associations = mutableListOf(terminalToNode),
    )

    private val connectivityNode = Class(
        name = "ConnectivityNode",
        description = """\[ZBEX\] Pretend extension.""",
        associations = mutableListOf(terminalToNode.swap()),
    )

    private val acdcTerminal = Class(name = "ACDCTerminal")

    private val phaseCode = Class(
        name = "PhaseCode",
        attributes = mutableListOf(Attribute("ABC", null), Attribute("Y", null, description = "/[ZBEX/] Unknown phase.")),
    )

    private val conductingEquipment = Class(
        name = "ConductingEquipment",
        associations = mutableListOf(Link("ConductingEquipment", "BaseVoltage", targetCardinality = "0..1", targetName = "baseVoltage")),
    )

    private val baseVoltage = Class(name = "BaseVoltage")

    private val builder = DiagramModelBuilder(
        classIndexOf(
            terminal,
            connectivityNode,
            acdcTerminal,
            phaseCode,
            conductingEquipment,
            baseVoltage
        )
    )

    private fun build(vararg classes: String, show: DiagramContent = DiagramContent.BOTH) =
        builder.build(DiagramDefinition("Test/Diagram", show = show, classes = classes.map(::DiagramClass)))

    @Test
    fun `show controls which relationships are drawn`() {
        val classes = arrayOf("Terminal", "ACDCTerminal", "ConnectivityNode")

        build(*classes, show = DiagramContent.BOTH).let {
            assertEquals(1, it.generalisations.size)
            assertEquals(1, it.associations.size)
        }
        build(*classes, show = DiagramContent.INHERITANCE).let {
            assertEquals(1, it.generalisations.size)
            assertTrue(it.associations.isEmpty())
        }
        build(*classes, show = DiagramContent.RELATIONSHIPS).let {
            assertTrue(it.generalisations.isEmpty())
            assertEquals(1, it.associations.size)
        }
    }

    private fun buildWith(show: DiagramContent, vararg classes: DiagramClass) =
        builder.build(DiagramDefinition("Test/Diagram", show = show, classes = classes.toList()))

    @Test
    fun `a class can show its relationships in an inheritance diagram`() {
        val model = buildWith(
            DiagramContent.INHERITANCE,
            DiagramClass("Terminal", DiagramContent.BOTH), DiagramClass("ACDCTerminal"), DiagramClass("ConnectivityNode"),
        )

        assertEquals(1, model.generalisations.size)
        assertEquals(1, model.associations.size, "One end's override is enough")
    }

    @Test
    fun `a class can hide its relationships in a diagram showing both`() {
        val model = buildWith(
            DiagramContent.BOTH,
            DiagramClass("Terminal"), DiagramClass("ACDCTerminal"), DiagramClass("ConnectivityNode", DiagramContent.INHERITANCE),
        )

        assertEquals(1, model.generalisations.size)
        assertTrue(model.associations.isEmpty())
    }

    @Test
    fun `a class can hide its inheritance`() {
        val model = buildWith(
            DiagramContent.BOTH,
            DiagramClass("Terminal", DiagramContent.RELATIONSHIPS), DiagramClass("ACDCTerminal"), DiagramClass("ConnectivityNode"),
        )

        assertTrue(model.generalisations.isEmpty())
        assertEquals(1, model.associations.size)
    }

    @Test
    fun `the more restrictive override wins when both ends disagree`() {
        val model = buildWith(
            DiagramContent.INHERITANCE,
            DiagramClass("Terminal", DiagramContent.BOTH), DiagramClass("ConnectivityNode", DiagramContent.INHERITANCE),
        )

        assertTrue(model.associations.isEmpty())
    }

    @Test
    fun `superclasses missing from the diagram are recorded for the corner of the box`() {
        assertEquals(listOf("ACDCTerminal"), build("Terminal", "ConnectivityNode").nodes.first { it.name == "Terminal" }.absentParents)
        assertEquals(emptyList(), build("Terminal", "ACDCTerminal").nodes.first { it.name == "Terminal" }.absentParents)
        assertEquals(emptyList(), build("ConnectivityNode").nodes.single().absentParents, "Classes without superclasses have none")
    }

    @Test
    fun `classes can be plain names or maps with options`() {
        val file = DiagramFile.parse(
            """
            diagrams:
              - path: A
                show: inheritance
                classes:
                  - Meter
                  - name: UsagePoint
                    show: both
                  - name: EndDevice
            """.trimIndent()
        )

        assertEquals(
            listOf(DiagramClass("Meter"), DiagramClass("UsagePoint", DiagramContent.BOTH), DiagramClass("EndDevice")),
            file.diagrams.single().classes,
        )
    }

    @Test
    fun `show is read from the definitions file and defaults to both`() {
        val file = DiagramFile.parse(
            """
            diagrams:
              - path: A
                classes: [X]
              - path: B
                show: inheritance
                classes: [X]
              - path: C
                show: relationships
                classes: [X]
            """.trimIndent()
        )

        assertEquals(listOf(DiagramContent.BOTH, DiagramContent.INHERITANCE, DiagramContent.RELATIONSHIPS), file.diagrams.map { it.show })
    }

    @Test
    fun `associations recorded in both classes become one edge`() {
        val model = build("Terminal", "ConnectivityNode")

        assertEquals(1, model.associations.size)
        val link = model.associations.single()
        val labels = link.labels()
        val (terminalEnd, nodeEnd) = if (link.source == "Terminal") labels.source to labels.target else labels.target to labels.source
        assertEquals("+terminals 0..*", terminalEnd)
        assertEquals("+connectivityNode 0..1", nodeEnd)
    }

    @Test
    fun `one sided associations keep the details they have`() {
        val link = build("ConductingEquipment", "BaseVoltage").associations.single()

        val labels = link.labels()
        val (ceEnd, bvEnd) = if (link.source == "ConductingEquipment") labels.source to labels.target else labels.target to labels.source
        assertEquals(null, ceEnd)
        assertEquals("+baseVoltage 0..1", bvEnd)
    }

    @Test
    fun `references to classes outside the diagram are omitted`() {
        val model = build("Terminal", "ConductingEquipment")

        assertEquals(listOf("Terminal", "ConductingEquipment"), model.nodes.map { it.name })
        assertTrue(model.associations.isEmpty())
        assertTrue(model.generalisations.isEmpty())
    }

    @Test
    fun `generalisations are drawn when both ends are present`() {
        assertEquals(listOf(Generalisation("Terminal", "ACDCTerminal")), build("Terminal", "ACDCTerminal").generalisations)
    }

    @Test
    fun `unknown classes are reported and skipped`() {
        val model = build("Terminal", "Missing")

        assertEquals(listOf("Terminal"), model.nodes.map { it.name })
        assertEquals(listOf("Missing"), model.unknownClasses)
    }

    @Test
    fun `attributes and enums are formatted`() {
        val nodes = build("Terminal", "PhaseCode", "ConnectivityNode").nodes.associateBy { it.name }

        assertEquals(emptyList(), nodes.getValue("Terminal").stereotypes)
        assertEquals(
            listOf(AttributeRow("+ phases: PhaseCode", false), AttributeRow("+ sequenceNumber: Integer [0..1]", false)),
            nodes.getValue("Terminal").rows,
        )

        assertEquals(listOf("enumeration"), nodes.getValue("PhaseCode").stereotypes)
        assertEquals(listOf(AttributeRow("ABC", false), AttributeRow("Y", true)), nodes.getValue("PhaseCode").rows)

        assertEquals(listOf("ZBEX"), nodes.getValue("ConnectivityNode").stereotypes)
        assertTrue(nodes.getValue("ConnectivityNode").isZbex)
    }

}
