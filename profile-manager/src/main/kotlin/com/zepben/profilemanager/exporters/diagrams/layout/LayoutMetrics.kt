/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import kotlin.math.abs
import kotlin.math.ln

/**
 * Measures of how readable a [DiagramLayout] is. Lower is better for everything except [density].
 *
 * @property crossings Places where two different edges cross.
 * @property nodeHits Edge segments passing through a class box that isn't one of the edge's ends.
 * @property labelOverlaps Edge labels overlapping another label or a class box (see [LabelConflicts]).
 * @property labelLineHits Lines passing through an edge label (see [LabelConflicts]).
 * @property density Total class box area as a fraction of the diagram area (higher is more compact).
 * @property area Width x height of the diagram.
 * @property aspectRatio Width / height of the diagram.
 */
data class LayoutMetrics(
    val crossings: Int,
    val nodeHits: Int,
    val labelOverlaps: Int,
    val labelLineHits: Int,
    val density: Double,
    val area: Double,
    val aspectRatio: Double,
) {

    /**
     * A single number to compare layouts of the same diagram by. Crossings and edges through boxes are what make a
     * diagram hard to follow, so they dominate; wasted space breaks ties between otherwise similar layouts.
     */
    val score: Double get() =
        crossings * 1.0 + nodeHits * 3.0 + labelOverlaps * 0.5 + labelLineHits * 0.5 + (1.0 - density) * 4.0 + aspectPenalty

    /** Diagrams are read on screens and in docs pages, so prefer landscape-ish shapes over very tall or very wide ones. */
    val aspectPenalty: Double get() = abs(ln(aspectRatio / 1.4)) * 2.0

    companion object {

        fun of(layout: DiagramLayout): LayoutMetrics {
            val segments = layout.edgeSegments
            val crossingCount = crossings(segments).size

            var nodeHits = 0
            layout.edges.forEachIndexed { i, edge ->
                layout.boxes.filterKeys { it != edge.source && it != edge.target }.values.forEach { box ->
                    nodeHits += segments[i].count { box.grow(-1.0).crossedBy(it) }
                }
            }

            // Each pair of overlapping labels is counted once, by comparing each label only with those after it.
            val labels = layout.edges.flatMap { it.labels }
            val labelConflicts = labels.mapIndexed { i, label -> LabelConflicts.of(label.box(), layout, labels.drop(i + 1)) }

            val area = layout.width * layout.height
            val nodeArea = layout.nodes.sumOf { it.width * it.height }

            return LayoutMetrics(
                crossings = crossingCount,
                nodeHits = nodeHits,
                labelOverlaps = labelConflicts.sumOf { it.overlaps },
                labelLineHits = labelConflicts.sumOf { it.lineHits },
                density = if (area > 0) nodeArea / area else 0.0,
                area = area,
                aspectRatio = if (layout.height > 0) layout.width / layout.height else 1.0,
            )
        }

    }

}

/**
 * What a label at [box] would conflict with: the lines of [layout] passing through it, and the class boxes of [layout]
 * and the [others] labels it overlaps. The box is shrunk slightly first, so a label placed exactly alongside a line,
 * class or other label doesn't count as touching it. Used both to score layouts and to choose where labels go, so the
 * two always agree.
 */
internal data class LabelConflicts(val lineHits: Int, val overlaps: Int) {

    val total: Int get() = lineHits + overlaps

    companion object {

        fun of(box: Box, layout: DiagramLayout, others: List<PositionedLabel>): LabelConflicts {
            val inner = box.grow(-0.5)
            return LabelConflicts(
                lineHits = layout.segments.count { inner.intersects(it) },
                overlaps = layout.nodes.count { it.box().overlaps(inner) } + others.count { it.box().overlaps(inner) },
            )
        }

    }

}
