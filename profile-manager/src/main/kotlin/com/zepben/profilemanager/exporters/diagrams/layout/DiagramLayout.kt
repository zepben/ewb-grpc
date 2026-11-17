/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import com.zepben.profilemanager.exporters.diagrams.model.DiagramNode
import com.zepben.profilemanager.exporters.diagrams.LABEL_EDGE_MARGIN
import com.zepben.profilemanager.exporters.diagrams.model.EdgeKind
import kotlin.math.max

/**
 * A [DiagramNode] with its computed size.
 *
 * @property headerHeight The height of the header compartment (the whole box for a class with no attributes).
 * @property titleHeight The height of the parent, stereotype and name lines, centred within the header.
 */
data class SizedNode(val node: DiagramNode, val width: Double, val height: Double, val headerHeight: Double, val titleHeight: Double)

/**
 * A class box at its position in the diagram.
 */
data class PositionedNode(val sized: SizedNode, val x: Double, val y: Double) {
    val name get() = sized.node.name
    val width get() = sized.width
    val height get() = sized.height
}

/**
 * @property atSource True if the label describes the source end of its association, false for the target end.
 * @property alignEnd Draw the text against the right of the label's box rather than the left, because its line is on
 *   the right.
 */
data class PositionedLabel(
    val text: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val atSource: Boolean,
    val alignEnd: Boolean = false,
)

data class PositionedEdge(
    val kind: EdgeKind,
    val source: String,
    val target: String,
    val points: List<Point>,
    val labels: List<PositionedLabel>,
)

/**
 * Identifies a label in a [DiagramLayout]: label number [label] of edge number [edge]. Tidying moves labels and
 * reroutes edges, but never adds, removes or reorders edges, so a reference stays valid from one version of a layout
 * to the next.
 */
data class LabelRef(val edge: Int, val label: Int)

/**
 * A fully positioned diagram, ready to render.
 */
data class DiagramLayout(val nodes: List<PositionedNode>, val edges: List<PositionedEdge>, val width: Double, val height: Double) {

    val metrics: LayoutMetrics by lazy { LayoutMetrics.of(this) }

    /** The line segments of each edge, in the same order as [edges]. */
    val edgeSegments: List<List<Segment>> by lazy { edges.map { it.points.zipWithNext() } }

    /** Every line segment of every edge. */
    val segments: List<Segment> by lazy { edgeSegments.flatten() }

    /** The box of each class, by name. */
    internal val boxes: Map<String, Box> by lazy { nodes.associate { it.name to it.box() } }

    operator fun get(ref: LabelRef): PositionedLabel = edges[ref.edge].labels[ref.label]

    /** The labels at an end of an association, i.e. the role and cardinality labels. */
    fun associationEndLabels(): List<LabelRef> = edges.flatMapIndexed { e, edge ->
        if (edge.kind != EdgeKind.ASSOCIATION || edge.points.size < 2)
            emptyList()
        else
            edge.labels.indices.map { LabelRef(e, it) }
    }

    /** This layout with edge [edgeIndex] replaced by [transform] of it. */
    fun withEdge(edgeIndex: Int, transform: (PositionedEdge) -> PositionedEdge): DiagramLayout =
        copy(edges = edges.mapIndexed { i, e -> if (i == edgeIndex) transform(e) else e })

    /** This layout with the label at [ref] replaced by [label]. */
    fun withLabel(ref: LabelRef, label: PositionedLabel): DiagramLayout =
        withEdge(ref.edge) { e -> e.copy(labels = e.labels.mapIndexed { j, l -> if (j == ref.label) label else l }) }

    /** Every label in the diagram except the one at [ref]. */
    fun labelsExcept(ref: LabelRef): List<PositionedLabel> =
        edges.flatMapIndexed { i, edge -> edge.labels.filterIndexed { j, _ -> i != ref.edge || j != ref.label } }

    /** Labels can be moved outside where ELK put things, so grow (and if needed shift) the diagram to keep them on it. */
    fun withLabelsInBounds(): DiagramLayout {
        val labels = edges.flatMap { it.labels }
        if (labels.isEmpty())
            return this

        val shiftX = max(0.0, LABEL_EDGE_MARGIN - labels.minOf { it.x })
        val shiftY = max(0.0, LABEL_EDGE_MARGIN - labels.minOf { it.y })
        val shifted = if (shiftX > 0 || shiftY > 0) shifted(shiftX, shiftY) else this
        return shifted.copy(
            width = max(width + shiftX, labels.maxOf { it.x + it.width } + shiftX + LABEL_EDGE_MARGIN),
            height = max(height + shiftY, labels.maxOf { it.y + it.height } + shiftY + LABEL_EDGE_MARGIN),
        )
    }

    /** Everything in the diagram moved by ([dx], [dy]), keeping its size. */
    fun shifted(dx: Double, dy: Double): DiagramLayout = copy(
        nodes = nodes.map { it.copy(x = it.x + dx, y = it.y + dy) },
        edges = edges.map { e ->
            e.copy(points = e.points.map { Point(it.x + dx, it.y + dy) }, labels = e.labels.map { it.copy(x = it.x + dx, y = it.y + dy) })
        },
    )

}
