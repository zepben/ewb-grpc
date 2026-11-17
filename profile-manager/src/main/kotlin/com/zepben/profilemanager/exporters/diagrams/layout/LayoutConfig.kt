/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import org.eclipse.elk.alg.layered.options.LayeringStrategy

/**
 * The layering and stacking choices [BestLayout] tries on each diagram. The rest of ELK's settings are fixed in
 * [ElkLayout].
 *
 * @property layering How classes are assigned to layers. Coffman-Graham with [maxNodesPerLayer] stops wide inheritance
 *   trees spreading every subclass across a single row.
 * @property layerByInheritance Assign layers by depth in the diagram's inheritance tree, so associations don't push
 *   classes into extra layers. Overrides [layering].
 * @property stackLeavesFrom Stack the leaf subclasses of a superclass one above the other (joined by a single line, as
 *   UML tools do) when it has at least this many, rather than spreading them across a layer. Null to never stack.
 * @property compact Close up the space between layers left by short boxes sharing a layer with tall ones.
 */
data class LayoutConfig(
    val name: String = "default",
    val layering: LayeringStrategy = LayeringStrategy.NETWORK_SIMPLEX,
    val maxNodesPerLayer: Int? = null,
    val layerByInheritance: Boolean = false,
    val stackLeavesFrom: Int? = 3,
    val compact: Boolean = true,
)
