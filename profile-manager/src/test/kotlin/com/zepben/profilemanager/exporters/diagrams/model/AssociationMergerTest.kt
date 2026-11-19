/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.model

import com.zepben.profilemanager.importers.YamlFileSystem
import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Link
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AssociationMergerTest {

    @Test
    fun `merging fills in the missing end of a one sided copy`() {
        val full = Link("A", "B", "0..1", "a", null, "0..*", "bs", null)
        val oneSided = Link("B", "A", targetCardinality = "0..1", targetName = "a")

        assertEquals(listOf(full), AssociationMerger.merge(listOf(oneSided, full)))
    }

    @Test
    fun `copies are paired even when the files disagree on role names`() {
        // As recorded in Equipment.yaml and EquipmentContainer.yaml.
        val fromEquipment = listOf(
            Link("Equipment", "EquipmentContainer", "0..*", "equipment", null, "0..1", "containers", null),
            Link("Equipment", "EquipmentContainer", "0..*", "equipment", null, "0..1", "currentContainers", null),
        )
        val fromContainer = listOf(
            Link("EquipmentContainer", "Equipment", "0..1", "equipmentContainer", null, "0..*", "equipment", null),
            Link("EquipmentContainer", "Equipment", "0..1", "equipmentContainer", null, "0..*", "currentEquipment", null),
        )

        val merged = AssociationMerger.merge(fromEquipment + fromContainer)

        // Each end is named by the class that owns it: the normal and current containers each pair with their equipment.
        assertEquals(
            setOf("+equipment 0..*" to "+containers 0..1", "+currentEquipment 0..*" to "+currentContainers 0..1"),
            merged.map { it.labels() }.map { if (merged.first().source == "Equipment") it.source to it.target else it.target to it.source }.toSet(),
        )
    }

    @Test
    fun `copies with nothing in common are not paired`() {
        val a = Link("A", "B", "0..1", "parent", null, "0..*", "children", null)
        val b = Link("B", "A", "1", "owner", null, "1..*", "items", null)

        assertEquals(2, AssociationMerger.merge(listOf(a, b)).size)
    }

    @Test
    fun `owners cardinality wins when copies with matching names disagree`() {
        // As recorded in Feeder.yaml and LvFeeder.yaml: Feeder.yaml guesses 0..1 for the end LvFeeder owns as 0..*.
        val fromFeeder = Link("Feeder", "LvFeeder", "0..1", "normalEnergizingFeeders", null, "0..*", "normalEnergizedLvFeeders", null)
        val fromLvFeeder = Link("LvFeeder", "Feeder", "0..*", "normalEnergizedLvFeeders", null, "0..*", "normalEnergizingFeeders", null)

        val merged = AssociationMerger.merge(listOf(fromFeeder, fromLvFeeder)).single()

        assertEquals(AssociationLabels("+normalEnergizingFeeders 0..*", "+normalEnergizedLvFeeders 0..*"), merged.labels())
    }

    @Test
    fun `distinct associations between the same classes are kept`() {
        val first = Link("A", "B", "0..1", "a", null, "0..*", "bs", null)
        val second = Link("A", "B", "0..1", "otherA", null, "0..*", "otherBs", null)

        assertEquals(2, AssociationMerger.merge(listOf(first, second, second.swap())).size)
    }

    @Test
    fun `each association between two classes in the ewb spec is drawn once`() {
        val classes = mutableListOf<Class>().also { list ->
            YamlFileSystem(File("../spec")).import("ewb").iterate(classCallback = { list += it })
        }
        val links = classes.flatMap { it.associations() }.filter { it.source != it.target }

        links.groupBy { setOf(it.source, it.target) }.forEach { (pair, group) ->
            val (a, b) = pair.toList()
            val expected = maxOf(group.count { it.source == a }, group.count { it.source == b })
            val merged = AssociationMerger.merge(group)

            if (System.getenv("PRINT_MERGES") != null && group.size > expected)
                println("$a <-> $b: " + merged.joinToString { "${it.source}[${it.labels().source}] - ${it.target}[${it.labels().target}]" })

            assertEquals(expected, merged.size, "Associations between $a and $b: $merged")
        }
    }

}
