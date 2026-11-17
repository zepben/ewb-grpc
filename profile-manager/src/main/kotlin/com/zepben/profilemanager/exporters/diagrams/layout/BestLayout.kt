/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import com.zepben.profilemanager.exporters.diagrams.model.DiagramModel
import com.zepben.profilemanager.exporters.printWarning
import org.eclipse.elk.alg.layered.options.LayeringStrategy

/**
 * Lays out a diagram with each of [candidates], tidies each (see [tidy]), and keeps the one with the best
 * [LayoutMetrics.score]. No single configuration suits every diagram: wide inheritance trees want bounded layers,
 * while association heavy diagrams do better with network simplex layering.
 */
class BestLayout(
    sizer: BoxSizer,
    candidates: List<LayoutConfig> = CANDIDATES,
    private val warn: (String) -> Unit = ::printWarning,
) {

    private val layouts = candidates.map { ElkLayout(sizer, it) }

    /**
     * The best layout of [model]. Candidates that fail are skipped, with a warning; if every one fails, so does this.
     */
    fun layout(model: DiagramModel): DiagramLayout {
        val failures = mutableListOf<Throwable>()
        val results = layouts.mapNotNull { layout ->
            runCatching { tidy(layout.layout(model)) }
                .onFailure {
                    failures += it
                    warn("layout '${layout.config.name}' failed for diagram '${model.definition.path}': ${it.message}")
                }
                .getOrNull()
        }
        if (results.isEmpty())
            throw IllegalStateException("Every layout failed for diagram '${model.definition.path}'", failures.firstOrNull())

        // The score is about readability; also penalise candidates for being bigger than the most compact one, so that
        // avoiding a single crossing doesn't win at the cost of a much taller diagram.
        val smallest = results.minOf { it.metrics.area }
        return results.minBy { it.metrics.score + SIZE_PENALTY * (it.metrics.area / smallest - 1) }
    }

    companion object {

        /** Score added per 100% of extra area over the most compact candidate. */
        const val SIZE_PENALTY = 3.0

        val CANDIDATES = listOf(
            LayoutConfig("coffman-graham", layering = LayeringStrategy.COFFMAN_GRAHAM, maxNodesPerLayer = 4),
            LayoutConfig("network simplex"),
            // Compaction can pull boxes onto edges when layers are fixed by inheritance depth.
            LayoutConfig("inheritance layers", layerByInheritance = true, compact = false),
        )

    }

}
