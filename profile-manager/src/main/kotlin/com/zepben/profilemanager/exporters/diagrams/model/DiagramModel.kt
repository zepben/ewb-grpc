/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.model

import com.zepben.profilemanager.exporters.ClassIndex
import com.zepben.profilemanager.exporters.ZBEX_STEREOTYPE
import com.zepben.profilemanager.exporters.isZbex
import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Link

private const val ENUMERATION_STEREOTYPE = "enumeration"

/**
 * A row in the attribute compartment of a class box.
 */
data class AttributeRow(val text: String, val isZbex: Boolean)

/**
 * A class box to draw.
 *
 * @property url The site relative URL of the class's documentation page.
 * @property absentParents Immediate superclasses that aren't in the diagram, so are named in the box's top right corner
 *   instead of being drawn.
 */
data class DiagramNode(
    val name: String,
    val url: String,
    val stereotypes: List<String>,
    val isZbex: Boolean,
    val rows: List<AttributeRow>,
    val absentParents: List<String> = emptyList(),
) {

    /** The text naming the [absentParents], or null if there are none. */
    val parentLabel: String? get() = absentParents.takeIf { it.isNotEmpty() }?.joinToString(", ")

}

/** The kinds of connector drawn between classes. */
enum class EdgeKind { GENERALISATION, ASSOCIATION }

/**
 * A generalisation, drawn from [sub] to [sup] with a hollow triangle at [sup].
 */
data class Generalisation(val sub: String, val sup: String)

/**
 * The text for each end of an association: the role name (if any) followed by the cardinality (if any).
 */
data class AssociationLabels(val source: String?, val target: String?)

fun Link.labels(): AssociationLabels = AssociationLabels(
    endLabel(sourceName, sourceCardinality),
    endLabel(targetName, targetCardinality),
)

private fun endLabel(role: String?, cardinality: String?): String? =
    listOfNotNull(role?.let { "+$it" }, cardinality).joinToString(" ").ifBlank { null }

/**
 * Everything that should be drawn in a diagram, with all references resolved to classes within the diagram.
 */
data class DiagramModel(
    val definition: DiagramDefinition,
    val nodes: List<DiagramNode>,
    val generalisations: List<Generalisation>,
    val associations: List<Link>,
    val unknownClasses: List<String>,
)

/**
 * Builds a [DiagramModel] from a [DiagramDefinition]. Only classes listed in the definition are drawn, and any
 * generalisations or associations to classes outside the definition are omitted. [DiagramDefinition.show], and the
 * per class [DiagramClass.show] overrides, control whether generalisations, associations or both are included.
 */
class DiagramModelBuilder(private val index: ClassIndex) {

    fun build(definition: DiagramDefinition): DiagramModel {
        val (known, unknown) = definition.classes.map { it.name }.distinct().partition { it in index }
        val classes = known.map { index[it]!! }
        val names = known.toSet()

        val nodes = classes.map { (cls, url) -> cls.toNode(url, absentParents = cls.ancestors().filter { it !in names }) }

        val generalisations = classes
            .flatMap { (cls, _) -> cls.ancestors().filter { it in names && it != cls.name }.map { Generalisation(cls.name, it) } }
            .filter { definition.shows(it.sub, it.sup, EdgeKind.GENERALISATION) }

        val associations = AssociationMerger.merge(
            classes.flatMap { (cls, _) -> cls.associations() }
                .filter { it.source in names && it.target in names }
                .filter { definition.shows(it.source, it.target, EdgeKind.ASSOCIATION) }
        )

        return DiagramModel(definition, nodes, generalisations, associations, unknown)
    }

    private fun Class.toNode(url: String, absentParents: List<String>): DiagramNode {
        val stereotypes = listOfNotNull(
            ENUMERATION_STEREOTYPE.takeIf { isEnum },
            ZBEX_STEREOTYPE.takeIf { description.isZbex() },
        )

        val rows = attributes().map { attr ->
            val text = if (isEnum)
                attr.name
            else
                buildString {
                    append("+ ").append(attr.name)
                    attr.type?.let { append(": ").append(it) }
                    if (attr.nullable == true)
                        append(" [0..1]")
                }
            AttributeRow(text, attr.description.isZbex())
        }

        return DiagramNode(name, url, stereotypes, description.isZbex(), rows, absentParents)
    }

}
