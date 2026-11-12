package com.zepben.profilemanager.exporters

import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Element
import com.zepben.profilemanager.models.Package
import com.zepben.profilemanager.models.Root
import java.io.File

class Docusaurus2Mdx(
    exportLocation: File,
    private val linkPrefix: String = "",
    private val validateAncestors: Boolean = false
) : Exporter(exportLocation) {

    private val linkMap = mutableMapOf<String, String>()
    private val classesByName = mutableMapOf<String, Class>()

    override fun export(e: Element) {
        val resolvedPrefix = if (linkPrefix.isNotBlank()) "/$linkPrefix" else linkPrefix
        buildMaps(e, resolvedPrefix)
        exportInternal(e, exportLocation)

        if (validateAncestors)
            compareClassHierarchy()
    }

    private fun buildMaps(e: Element, path: String = "") {
        if (e is Class) {
            linkMap[e.name] = path + "/" + e.name
            classesByName[e.name] = e
        } else {
            e.children.forEach { buildMaps(it, path + "/" + e.name) }
        }
    }

    private fun exportInternal(e: Element, exportLocation: File) {
        when (e) {
            is Package, is Root -> {
                val newLocation = exportLocation.createDirectoryInFolder(e.name)
                e.children.forEach { exportInternal(it, newLocation) }
            }

            is Class -> {
                val classFile = exportLocation.fileInFolder(e.name, ".mdx")
                classFile.createNewFile()
                classFile.writeText(classAsMarkdown(e))
            }
        }
    }

    private fun classAsMarkdown(node: Class): String {
        val ancestors = ancestorChain(node)
        return buildString {
            append(header(node.name))
            section(
                "${if (node.isEnum) "Enum" else "Class"} Description",
                node.description?.split("<br/>")?.joinToString("\n\n") { it.descriptionHtml() }.orEmpty()
            )
            if (node.isEnum)
                section("Values", enumValuesTable(node))
            else
                section("Attributes and Relationships", membersTable(node, ancestors))
            section("Hierarchy", hierarchyTree(node, ancestors))
        }
    }

    private fun StringBuilder.section(title: String, body: String) {
        append("## $title\n\n")
        if (body.isNotEmpty())
            append(body).append("\n\n")
    }

    /**
     * The frontmatter and imports that start every class page.
     */
    private fun header(name: String) = """
        |---
        |title: $name
        |hide_table_of_contents: true
        |slug: $name
        |sidebar_position: 0
        |---
        |
        |import Link from '@docusaurus/Link';
        |
        |
    """.trimMargin()

    /**
     * An ASCII tree of the full ancestor chain, siblings, and descendants of [node] up to [MAX_DESCENDANT_DEPTH] levels deep.
     *
     * @param ancestors The ancestors of [node], nearest first, as returned by [ancestorChain].
     */
    private fun hierarchyTree(node: Class, ancestors: List<Class>): String {
        if (ancestors.isEmpty() && node.descendants().isEmpty())
            return "None"

        val lines = mutableListOf<String>()
        val current = "<b>${node.name}</b>"
        val visited = mutableSetOf(node.name)

        /**
         * Adds a branch line under [prefix] for each of [entries] (see [collapse]), with collapsed entries shown as "… (n more)". Each named
         * entry is rendered by [render], followed by any lines [nested] adds beneath it using the child prefix it is given.
         */
        fun appendEntries(
            entries: List<Pair<String?, Int>>,
            prefix: String,
            render: (name: String) -> String,
            nested: (name: String, childPrefix: String) -> Unit
        ) {
            entries.forEachIndexed { i, (name, omittedCount) ->
                val last = i == entries.lastIndex
                if (name == null) {
                    lines.add(treeLine(prefix + branch(last), "… ($omittedCount more)"))
                } else {
                    lines.add(treeLine(prefix + branch(last), render(name)))
                    nested(name, prefix + indent(last))
                }
            }
        }

        fun appendDescendants(name: String, prefix: String, depth: Int) {
            val children = classesByName[name]?.descendants()?.filter { it !in visited } ?: return
            if (children.isEmpty())
                return

            if (depth > MAX_DESCENDANT_DEPTH) {
                lines.add(treeLine(prefix + branch(true), "…"))
                return
            }

            // Direct children are always listed in full; deeper levels can have entries omitted for brevity.
            val entries = collapse(children, centre = 0, max = if (depth == 1) children.size else MAX_LISTED_DESCENDANTS)
            appendEntries(entries, prefix, render = ::linkTo) { child, childPrefix ->
                visited.add(child)
                appendDescendants(child, childPrefix, depth + 1)
            }
        }

        val parent = ancestors.firstOrNull()
        if (parent == null) {
            lines.add(current)
            appendDescendants(node.name, "", 1)
        } else {
            ancestors.asReversed().forEachIndexed { i, ancestor ->
                if (i > 0)
                    lines.add(treeLine("│", ""))
                lines.add(linkTo(ancestor.name))
            }

            val siblings = parent.descendants().let { if (node.name in it) it else (it + node.name).sorted() }
            val entries = collapse(siblings, centre = siblings.indexOf(node.name), max = 1 + MAX_LISTED_SIBLINGS)
            appendEntries(
                entries,
                prefix = "",
                render = { if (it == node.name) current else linkTo(it) },
                nested = { name, childPrefix -> if (name == node.name) appendDescendants(name, childPrefix, 1) }
            )
        }

        return buildString {
            append("<div style={{fontFamily: 'var(--ifm-font-family-monospace)', whiteSpace: 'pre', lineHeight: 1.4}}>\n")
            append(lines.joinToString("<br />\n"))
            append("\n</div>")
        }
    }

    /**
     * A window of up to [max] of [items], centred on index [centre] where possible. Items outside the window are collapsed into entries with a
     * null name and the number of items omitted. A single item is never collapsed into "… (1 more)"; the window is extended to list it instead.
     */
    private fun collapse(items: List<String>, centre: Int, max: Int): List<Pair<String?, Int>> {
        val windowFrom = (centre - (max - 1) / 2).coerceIn(0, maxOf(0, items.size - max))
        val windowTo = minOf(items.size, windowFrom + max)
        val from = if (windowFrom > 1) windowFrom else 0
        val to = if (items.size - windowTo > 1) windowTo else items.size

        return buildList {
            if (from > 0) add(null to from)
            items.subList(from, to).forEach { add(it to 0) }
            if (to < items.size) add(null to items.size - to)
        }
    }

    private fun branch(last: Boolean) = if (last) "└── " else "├── "

    private fun indent(last: Boolean) = if (last) "    " else "│   "

    private fun treeLine(prefix: String, content: String) = "{\"$prefix\"}$content"

    private fun enumValuesTable(node: Class): String {
        if (node.attributes().isEmpty())
            return "None"

        return jsxTable(listOf("Value", "Description")) {
            node.attributes().forEach { appendRow(listOf(it.name.jsxSafe(), it.description.descriptionHtml())) }
        }
    }

    private fun membersTable(node: Class, ancestors: List<Class>): String {
        val ownRows = memberRows(node)
        val inheritedGroups = ancestors
            .map { it to memberRows(it) }
            .filter { (_, rows) -> rows.isNotEmpty() }

        if (ownRows.isEmpty() && inheritedGroups.isEmpty())
            return "None"

        return jsxTable(listOf("Name", "Type", "Description")) {
            ownRows.forEach { appendRow(it) }
            inheritedGroups.forEach { (ancestor, rows) ->
                appendGroupRow("<b><i>from ${linkTo(ancestor.name)}</i></b>", colSpan = 3)
                rows.forEach { appendRow(it) }
            }
        }
    }

    private fun jsxTable(headings: List<String>, body: StringBuilder.() -> Unit): String = buildString {
        append("<table>\n")
        append("  <thead>\n")
        append("    <tr>\n")
        headings.forEach { append("      <th>$it</th>\n") }
        append("    </tr>\n")
        append("  </thead>\n")
        append("  <tbody>\n")
        body()
        append("  </tbody>\n")
        append("</table>")
    }

    /**
     * Rows containing a Zepben extension pill (see [descriptionHtml]) are given the [ZBEX_ROW_CLASS] class so the whole row can be styled.
     */
    private fun StringBuilder.appendRow(cells: List<String>) {
        if (cells.any { ZBEX_PILL in it })
            append("    <tr className='$ZBEX_ROW_CLASS'>\n")
        else
            append("    <tr>\n")
        cells.forEach { append("      <td>$it</td>\n") }
        append("    </tr>\n")
    }

    private fun StringBuilder.appendGroupRow(content: String, colSpan: Int) {
        append("    <tr>\n")
        append("      <td colSpan={$colSpan} className='inherited-from'>$content</td>\n")
        append("    </tr>\n")
    }

    /**
     * Rows (name, type, description) for the attributes and associations declared **directly** on class [c].
     */
    private fun memberRows(c: Class): List<List<String>> {
        val attributeRows = c.attributes().map {
            listOf(it.name.jsxSafe(), linkTo(it.type), it.description.descriptionHtml())
        }
        val associationRows = c.associations().map { association ->
            val cardinality = association.targetCardinality?.let { " ($it)" } ?: ""
            listOf(
                (association.targetName ?: association.target).jsxSafe(),
                linkTo(association.target) + cardinality,
                association.targetDescription.descriptionHtml()
            )
        }
        return attributeRows + associationRows
    }

    /**
     * All ancestors of [node], nearest first. Assumes single inheritance (only the first declared ancestor is followed), and stops on cycles.
     */
    private fun ancestorChain(node: Class): List<Class> {
        val seen = mutableSetOf<String>()
        return generateSequence(node) { c -> c.ancestors().firstOrNull()?.let(classesByName::get) }
            .takeWhile { seen.add(it.name) }
            .drop(1)
            .toList()
    }

    private fun linkTo(name: String?): String {
        if (name == null) return ""

        val lookup = if (name.endsWith("List")) name.dropLast(4).trim() else name

        return linkMap[lookup]?.let { "<Link to='$it'>$name</Link>" } ?: name.jsxSafe()
    }

    private fun compareClassHierarchy() {
        // Descendants of each class, as derived from the ancestors declared by each descendant.
        val classesByAncestor = classesByName.values
            .flatMap { c -> c.ancestors().map { it to c.name } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }
        // Descendants of each class, as declared by the class itself (its own descendant list).
        val classesByDescendant = classesByName.values
            .filter { it.descendants().isNotEmpty() }
            .associate { it.name to it.descendants().toSet() }

        var onDots = true
        fun report(label: String, items: Set<String>) {
            if (items.isEmpty()) return
            // Make sure we have moved off the "..." from the profile generation log line.
            if (onDots) println()
            onDots = false
            println(label)
            items.forEach { println("   $it") }
        }

        report("missingKeysFromAncestors:", classesByAncestor.keys - classesByDescendant.keys)
        report("missingKeysFromDescendants:", classesByDescendant.keys - classesByAncestor.keys)

        classesByAncestor.forEach { (cls, inAncestors) ->
            val inDescendants = classesByDescendant[cls] ?: return@forEach
            report("$cls missingDescendantsLink:", inAncestors - inDescendants)
            report("$cls missingAncestorLink:", inDescendants - inAncestors)
        }
    }

    companion object {
        // Number of levels of descendants shown below the current class in the hierarchy tree; anything deeper is shown as "…".
        private const val MAX_DESCENDANT_DEPTH = 2

        // Number of siblings listed around the current class in the hierarchy tree (not counting the current class itself).
        // One extra sibling may be listed at either end rather than collapsing it into "… (1 more)".
        private const val MAX_LISTED_SIBLINGS = 5

        // Number of grandchildren (or deeper) listed under a single class in the hierarchy tree. Direct children are always listed in full.
        // One extra class may be listed rather than collapsing it into "… (1 more)".
        private const val MAX_LISTED_DESCENDANTS = 5
    }

}

// Marker used in descriptions to flag Zepben extensions to the CIM, and the markup it is rendered as.
private const val ZBEX_MARKER = "[ZBEX]"
private const val ZBEX_PILL = "<span className='zbex-pill'>ZBEX</span>"
private const val ZBEX_ROW_CLASS = "zbex-row"

/**
 * [jsxSafe] text with any [ZBEX_MARKER] replaced by a [ZBEX_PILL].
 */
private fun String?.descriptionHtml(): String = jsxSafe().replace(ZBEX_MARKER, ZBEX_PILL)

/**
 * Escapes text so MDX treats it as literal text rather than JSX/markdown syntax, converting newlines to explicit line breaks.
 * Markdown-escaped brackets (`\[`, `\]`) are unescaped first, as they would otherwise be rendered with their backslashes.
 */
private fun String?.jsxSafe(): String = this?.replace("\\[", "[")
    ?.replace("\\]", "]")
    ?.replace("<", "&lt;")
    ?.replace(">", "&gt;")
    ?.replace("{", "&#123;")
    ?.replace("}", "&#125;")
    ?.replace("\n", "<br />")
    ?: ""
