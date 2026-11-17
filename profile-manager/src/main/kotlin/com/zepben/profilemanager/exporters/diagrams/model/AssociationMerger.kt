/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.model

import com.zepben.profilemanager.models.Link

/**
 * Collapses the copies of each association recorded in the spec into a single line per association. See [merge].
 */
object AssociationMerger {

    /**
     * Most associations are recorded in the files of both classes involved: each class's file lists the association
     * from its own side, with itself as the source. Collapse these into a single line per association.
     *
     * Each file is the authority for the end it owns (its `target*` fields, i.e. the property on that class), but
     * the role names each file guesses for the far end often disagree with the other file. For example,
     * `Equipment.yaml` has `equipment <-> containers` and `equipment <-> currentContainers`, while
     * `EquipmentContainer.yaml` has `equipmentContainer <-> equipment` and `equipmentContainer <-> currentEquipment`.
     * So rather than requiring the names to match, the associations owned by each class are paired one to one,
     * preferring pairs whose role names agree (exactly, or by sharing words such as "current" and "container").
     * Associations recorded by only one side are kept as they are.
     */
    fun merge(links: List<Link>): List<Link> =
        links.distinct().groupBy { setOf(it.source, it.target) }.values.flatMap { group ->
            val first = group.first()
            if (first.source == first.target)
                mergeSelfAssociations(group)
            else {
                // Orient the merged lines from the alphabetically first class, so results don't depend on file order.
                val (owner, other) = listOf(first.source, first.target).sorted()
                pairUp(group.filter { it.source == owner }, group.filter { it.source == other })
            }
        }

    /**
     * Pair each association owned by one class with one owned by the other, maximising first the number of pairs and
     * then how well their role names agree. Pairs must have compatible cardinalities.
     */
    private fun pairUp(fromA: List<Link>, fromB: List<Link>): List<Link> {
        val partners = bestPairing(fromA, fromB).toMap()
        val pairedB = partners.values.toSet()
        return fromA.mapIndexed { i, a -> partners[i]?.let { mergePair(a, fromB[it]) } ?: a } +
            fromB.filterIndexed { j, _ -> j !in pairedB }
    }

    private fun bestPairing(fromA: List<Link>, fromB: List<Link>): List<Pair<Int, Int>> {
        val scores = fromA.map { a -> fromB.map { b -> pairScore(a, b) } }
        var best = emptyList<Pair<Int, Int>>()
        var bestScore = Double.NEGATIVE_INFINITY

        // Exhaustive search: the number of associations between two classes is tiny.
        fun search(i: Int, used: Set<Int>, chosen: List<Pair<Int, Int>>, score: Double) {
            if (i == fromA.size) {
                val total = chosen.size * 1000.0 + score
                if (total > bestScore) {
                    bestScore = total
                    best = chosen
                }
                return
            }
            search(i + 1, used, chosen, score)
            fromB.indices.filter { it !in used && scores[i][it] != null }.forEach { j ->
                search(i + 1, used + j, chosen + (i to j), score + scores[i][j]!!)
            }
        }

        search(0, emptySet(), emptyList(), 0.0)
        return best
    }

    /**
     * How well [a] (owned by one class) matches [b] (owned by the other), or null if nothing suggests they are the same
     * association. [a]'s target end is the same end as [b]'s source end, and vice versa.
     *
     * Neither the role names nor the cardinalities a file gives for the far end are reliable (e.g. `Feeder.yaml`
     * gives `0..1` for the end `LvFeeder.yaml` owns as `normalEnergizingFeeders 0..*`), so they are evidence rather
     * than requirements: a pair needs either compatible cardinalities or agreeing role names.
     */
    private fun pairScore(a: Link, b: Link): Double? {
        val cardinalitiesAgree = compatible(a.targetCardinality, b.sourceCardinality) && compatible(a.sourceCardinality, b.targetCardinality)

        var namesScore = 0.0
        if (a.targetName != null && a.targetName == b.sourceName) namesScore += 2
        if (a.sourceName != null && a.sourceName == b.targetName) namesScore += 2

        val aWords = words(a.sourceName) + words(a.targetName)
        val bWords = words(b.sourceName) + words(b.targetName)
        if (aWords.isNotEmpty() && bWords.isNotEmpty())
            namesScore += (aWords intersect bWords).size.toDouble() / (aWords union bWords).size

        if (!cardinalitiesAgree && namesScore == 0.0)
            return null

        return namesScore + if (cardinalitiesAgree) 1.0 else 0.0
    }

    /**
     * Merge two copies of the same association, preferring each class's own description of the end it owns: [a] owns
     * the target end, and [b] (recorded from the other side) the source end.
     */
    private fun mergePair(a: Link, b: Link): Link = merged(source = b.swap(), target = a)

    /** The words in a camelCase role name, singular and lower case, e.g. `currentContainers` -> {current, container}. */
    private fun words(name: String?): Set<String> =
        name?.split(Regex("(?<=[a-z0-9])(?=[A-Z])"))
            ?.map { it.lowercase().removeSuffix("s") }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    /**
     * Associations from a class to itself are all recorded in the same file, once from each end, so merge copies
     * whose ends match (in either orientation).
     */
    private fun mergeSelfAssociations(links: List<Link>): List<Link> {
        val merged = mutableListOf<Link>()
        links.forEach { link ->
            val index = merged.indexOfFirst { it.sameEnds(link) || it.sameEnds(link.swap()) }
            if (index >= 0)
                merged[index] = merged[index].fillFrom(if (merged[index].sameEnds(link)) link else link.swap())
            else
                merged.add(link)
        }
        return merged
    }

    private fun Link.sameEnds(o: Link) = compatible(sourceName, o.sourceName) &&
        compatible(targetName, o.targetName) &&
        compatible(sourceCardinality, o.sourceCardinality) &&
        compatible(targetCardinality, o.targetCardinality)

    private fun Link.fillFrom(o: Link) = merged(source = this, target = this, sourceFallback = o, targetFallback = o)

    /**
     * An association between [target]'s classes, with its source end as described by [source] and its target end as
     * described by [target]. Anything either leaves out is taken from [sourceFallback] or [targetFallback].
     */
    private fun merged(source: Link, target: Link, sourceFallback: Link = target, targetFallback: Link = source) = target.copy(
        sourceCardinality = source.sourceCardinality ?: sourceFallback.sourceCardinality,
        sourceName = source.sourceName ?: sourceFallback.sourceName,
        sourceDescription = source.sourceDescription ?: sourceFallback.sourceDescription,
        targetCardinality = target.targetCardinality ?: targetFallback.targetCardinality,
        targetName = target.targetName ?: targetFallback.targetName,
        targetDescription = target.targetDescription ?: targetFallback.targetDescription,
    )

    private fun compatible(a: String?, b: String?) = a == null || b == null || a == b

}
