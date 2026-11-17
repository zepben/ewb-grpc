/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import com.zepben.profilemanager.exporters.diagrams.model.DiagramModel
import com.zepben.profilemanager.models.Link

/**
 * A superclass and the leaf subclasses to draw stacked one above the other beneath it, in diagram order.
 */
data class LeafStack(val parent: String, val leaves: List<String>)

/**
 * Decisions about a diagram's structure that guide the layout, made before (and independently of) ELK: which way each
 * association points, which leaf subclasses are stacked, and which layer each class goes in when layering by
 * inheritance. Layers count up from the bottom, as edges point up from subclass to superclass.
 */
internal object LayerPlanner {

    /**
     * Every edge pushes ELK to put its target in a higher layer than its source, so the direction of each association
     * decides how tall the diagram gets. Point them the way UML diagrams are usually drawn: the "many" end below the
     * "one" end, so wholes sit above their parts (e.g. GeographicalRegion above SubGeographicalRegion above Substation).
     *
     * One-to-one and many-to-many associations are then pointed, one at a time, so the class with the longer chain of
     * classes already above it goes underneath. The association then fits within height the diagram needs anyway,
     * instead of stacking one chain on top of another.
     */
    fun orientAssociations(model: DiagramModel): List<Link> {
        val up = mutableMapOf<String, MutableSet<String>>()
        fun addEdge(below: String, above: String) {
            up.getOrPut(below) { mutableSetOf() } += above
        }
        model.generalisations.forEach { addEdge(it.sub, it.sup) }

        val decided = model.associations.map { link ->
            val sourceMany = link.sourceCardinality.isMany()
            val targetMany = link.targetCardinality.isMany()
            when {
                sourceMany && !targetMany -> link
                targetMany && !sourceMany -> link.swap()
                else -> null
            }?.also { addEdge(it.source, it.target) }
        }

        return model.associations.zip(decided).map { (link, oriented) ->
            oriented ?: run {
                // The graph grows with each association pointed, so measure it afresh each time.
                val heightAbove = longestPaths(up)
                (if (heightAbove(link.target) > heightAbove(link.source)) link.swap() else link)
                    .also { addEdge(it.source, it.target) }
            }
        }
    }

    /**
     * Leaves are subclasses whose only relationship in the diagram is their generalisation to a single superclass. Each
     * superclass with at least [threshold] of them gets them stacked, rather than spread across a layer.
     */
    fun leafStacks(model: DiagramModel, threshold: Int?): List<LeafStack> {
        if (threshold == null)
            return emptyList()

        val degree = mutableMapOf<String, Int>()
        model.generalisations.forEach { degree.merge(it.sub, 1, Int::plus); degree.merge(it.sup, 1, Int::plus) }
        model.associations.forEach { degree.merge(it.source, 1, Int::plus); degree.merge(it.target, 1, Int::plus) }

        val order = model.nodes.withIndex().associate { it.value.name to it.index }
        return model.generalisations
            .filter { degree[it.sub] == 1 }
            .groupBy({ it.sup }, { it.sub })
            .filter { it.value.size >= threshold }
            .map { (parent, leaves) -> LeafStack(parent, leaves.sortedBy { order[it] }) }
    }

    /**
     * The layer of every class when layering by inheritance. Each class in the diagram's inheritance is placed as close
     * below its superclass as possible: one layer below the superclass's own layer. Classes that only have
     * associations are placed one layer from a class they're associated with, rather than all on the top layer.
     */
    fun inheritanceLayers(model: DiagramModel, associations: List<Link>): Map<String, Int> {
        val height = longestPaths(model.generalisations.groupBy({ it.sub }, { it.sup }))
        val members = model.generalisations.flatMap { listOf(it.sub, it.sup) }.toSet()
        val heights = members.associateWith { height(it) }
        val max = heights.values.maxOrNull() ?: 0

        val layers = heights.mapValues { max - it.value }.toMutableMap()
        val pending = model.nodes.map { it.name }.filter { it !in layers }.toMutableSet()
        while (pending.isNotEmpty()) {
            val placed = associations.firstNotNullOfOrNull { link ->
                when {
                    link.source in pending && link.target in layers -> link.source to layers.getValue(link.target) - 1
                    link.target in pending && link.source in layers -> link.target to layers.getValue(link.source) + 1
                    else -> null
                }
            } ?: (pending.first() to 0)

            layers[placed.first] = placed.second
            pending.remove(placed.first)
        }

        return layers
    }

    private fun String?.isMany(): Boolean {
        if (this == null) return false
        val upper = substringAfter("..", this).trim()
        return upper == "*" || upper == "n" || upper.toIntOrNull()?.let { it > 1 } == true
    }

    /**
     * The number of edges on the longest path up from each node of the graph [up], ignoring any edge that would close
     * a cycle. Each node is measured once, so this is linear in the size of the graph.
     */
    private fun longestPaths(up: Map<String, Collection<String>>): (String) -> Int {
        val memo = mutableMapOf<String, Int>()
        val onPath = mutableSetOf<String>()

        fun height(name: String): Int = memo[name] ?: run {
            onPath += name
            val result = up[name].orEmpty().filter { it !in onPath }.maxOfOrNull { height(it) + 1 } ?: 0
            onPath -= name
            result.also { memo[name] = it }
        }

        return ::height
    }

}
