/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/**
 * Places the role and cardinality labels at the ends of associations. ELK puts each label next to the end of its own
 * line, but doesn't stop other lines running through it. Labels that a line passes through (or that overlap a class or
 * another label) are moved to the position near the same end with the fewest such conflicts. It isn't always possible
 * to avoid every line, in which case the least bad position is used.
 */
object LabelPlacer {

    /** How far labels sit from the line and the class they label. */
    internal const val GAP = 3.0

    /** Distances along the line, from the end it labels, at which to try placing a label. */
    private val ALONG = listOf(GAP, 10.0, 18.0, 28.0, 40.0)

    /** Distances to either side of the line at which to try placing a label, nearest (preferred) first. */
    private val ASIDE = listOf(GAP, 12.0, 22.0)

    /** The space ELK leaves between a label and the line it sits to the right of, used for labels on the left too. */
    private const val SIDE_GAP = 5.0

    internal data class Placement(val label: PositionedLabel, val conflicts: LabelConflicts)

    /** Move any association labels that conflict with lines, classes or other labels to better positions. */
    fun improve(layout: DiagramLayout): DiagramLayout = layout.mapAssociationLabels { slot ->
        val existing = slot.conflicts(slot.label)
        if (existing == 0)
            return@mapAssociationLabels slot.label

        val best = best(slot.label, slot.end, slot.next, slot.layout, slot.others)
        if (best.conflicts.total < existing) best.label else slot.label
    }

    /**
     * Make labels hug their line equally on either side. Text is drawn from the left of a label's box, and boxes are a
     * little wider than their text (in case a browser's font renders wider), so a label to the left of its line used to
     * leave a bigger gap than one to the right. ELK also leaves labels on the left of a line further from it. So labels
     * to the left of a line are drawn right aligned, and labels beside a vertical line are placed the same distance from
     * it on either side (where that doesn't make them overlap anything).
     */
    fun alignToLines(layout: DiagramLayout): DiagramLayout = layout.mapAssociationLabels { slot ->
        val label = slot.label
        val end = slot.end
        val onLeft = label.x + label.width / 2 < end.x

        val placed = label.copy(alignEnd = onLeft && label.x + label.width <= end.x + 0.5)
        if (abs(end.x - slot.next.x) >= 0.5)
            return@mapAssociationLabels placed

        val snapped = placed.copy(x = if (onLeft) end.x - SIDE_GAP - label.width else end.x + SIDE_GAP, alignEnd = onLeft)
        if (slot.conflicts(snapped) <= slot.conflicts(label)) snapped else placed
    }

    /**
     * A label at one end of an association, as seen while [mapAssociationLabels] works through them.
     *
     * @property layout The layout so far, with the labels before this one already replaced.
     * @property end The end of the line the label describes.
     * @property next The next point along the line from [end].
     */
    private class LabelSlot(val layout: DiagramLayout, ref: LabelRef) {

        val label: PositionedLabel = layout[ref]

        private val endAndNext = layout.edges[ref.edge].endAndNext(label.atSource)
        val end: Point get() = endAndNext.first
        val next: Point get() = endAndNext.second

        /** Every other label in the diagram. */
        val others: List<PositionedLabel> by lazy { layout.labelsExcept(ref) }

        /** The number of conflicts [candidate] would have in place of this label. */
        fun conflicts(candidate: PositionedLabel): Int = LabelConflicts.of(candidate.box(), layout, others).total

    }

    /**
     * Replace each label at an end of an association with [place] of it, one at a time, so each sees the labels
     * placed before it.
     */
    private fun DiagramLayout.mapAssociationLabels(place: (LabelSlot) -> PositionedLabel): DiagramLayout =
        associationEndLabels().fold(this) { current, ref -> current.withLabel(ref, place(LabelSlot(current, ref))) }

    /**
     * The position for [label] at [end] of a line heading towards [towards] with the fewest conflicts, preferring
     * positions closest to the end. Positions step away from the end along the line, on either side of it, then further out
     * to the side.
     */
    internal fun best(label: PositionedLabel, end: Point, towards: Point, layout: DiagramLayout, others: List<PositionedLabel>): Placement {
        val length = hypot(towards.x - end.x, towards.y - end.y).takeIf { it > 1e-6 }
            ?: return Placement(label, LabelConflicts(Int.MAX_VALUE, 0))
        val ux = (towards.x - end.x) / length
        val uy = (towards.y - end.y) / length

        return ASIDE.flatMap { aside -> ALONG.flatMap { along -> listOf(1.0, -1.0).map { side -> Triple(along, side, aside) } } }
            .map { (along, side, aside) ->
                // Just off the line on the chosen side. The label extends away from the line, and away from the class
                // along the line (or, for a diagonal, away from the line in both directions so only its corner is near).
                val nx = -uy * side
                val ny = ux * side
                val anchorX = end.x + ux * along + nx * aside
                val anchorY = end.y + uy * along + ny * aside
                val xSign = if (abs(nx) > 1e-9) sign(nx) else sign(ux)
                val ySign = if (abs(ny) > 1e-9) sign(ny) else sign(uy)
                val x = if (xSign >= 0) anchorX else anchorX - label.width
                val y = if (ySign >= 0) anchorY else anchorY - label.height
                label.copy(x = x, y = y)
            }
            .map { Placement(it, LabelConflicts.of(it.box(), layout, others)) }
            .minBy { it.conflicts.total } // minBy keeps the first, i.e. closest, of equally good positions.
    }

    /** The end of this edge's line at its source (or target), and the next point along the line from it. */
    private fun PositionedEdge.endAndNext(atSource: Boolean): Segment =
        if (atSource) points[0] to points[1] else points[points.size - 1] to points[points.size - 2]

}
