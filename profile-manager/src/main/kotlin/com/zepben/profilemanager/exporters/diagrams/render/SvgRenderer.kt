/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.render

import com.zepben.profilemanager.exporters.diagrams.layout.*
import com.zepben.profilemanager.exporters.diagrams.*
import com.zepben.profilemanager.exporters.diagrams.model.EdgeKind
import kotlin.math.ceil

/**
 * Renders a [DiagramLayout] as an SVG document. Layout loosely follows Enterprise Architect's CIM diagrams: class boxes
 * with a bold name header and an attribute compartment, solid lines with a hollow triangle for generalisations, and
 * plain lines labelled with role and cardinality for associations. Zepben extensions (ZBEX) get a solid header
 * (classes) or a tinted row and marker (attributes).
 *
 * The background is transparent, and every colour comes from a CSS custom property that defers to the Docusaurus
 * (Infima) theme variables of the page the SVG is rendered into, so a diagram follows the site's light/dark mode. This
 * only works when the SVG is part of the page's DOM (e.g. imported into MDX as a component); an SVG loaded through
 * `<img>` can't see the page's styles, so falls back to the data model site's light theme colours.
 *
 * The SVG has no links of its own: links inside an SVG can't get the site's base URL or go through its router. Instead,
 * each linked class box carries its page's site relative path in [HREF_ATTRIBUTE], and the page showing the diagram
 * wraps those boxes in Docusaurus links (see [diagramPageMdx]).
 *
 * @param links Whether to mark class boxes with the page to link them to.
 */
class SvgRenderer(private val fontFamily: String, private val links: Boolean = true) {

    fun render(layout: DiagramLayout, title: String? = null): String {
        val width = ceil(layout.width)
        val height = ceil(layout.height)

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<svg xmlns="http://www.w3.org/2000/svg" class="$ROOT_CLASS" """ +
                    """width="${svgNumber(width)}" height="${svgNumber(height)}" viewBox="0 0 ${svgNumber(width)} ${svgNumber(height)}">"""
            )
            title?.let { appendLine("  <title>${esc(it)}</title>") }
            appendDefs()

            appendLine("""  <g class="edges">""")
            val hops = LineHops.of(layout.edges)
            val drawings = SharedLines.of(layout.edges)
            layout.edges.forEachIndexed { i, edge -> appendEdge(edge, hops[i], drawings[i]) }
            appendLine("  </g>")

            appendLine("""  <g class="classes">""")
            layout.nodes.forEach { appendNode(it) }
            appendLine("  </g>")

            appendLine("""  <g class="edge-labels">""")
            layout.edges.flatMap { it.labels }.forEach { appendLabel(it) }
            appendLine("  </g>")

            appendLine("</svg>")
        }
    }

    private fun StringBuilder.appendDefs() {
        val A = svgNumber(GENERALISATION_ARROW)
        appendLine("  <defs>")
        appendLine(
            """    <marker id="uml-generalisation" viewBox="0 0 $A $A" refX="$A" refY="${svgNumber(GENERALISATION_ARROW / 2)}" markerWidth="$A" markerHeight="$A" """ +
                """markerUnits="userSpaceOnUse" orient="auto">"""
        )
        appendLine("""      <path class="arrow" d="M0,0 L$A,${svgNumber(GENERALISATION_ARROW / 2)} L0,$A Z"/>""")
        appendLine("    </marker>")
        appendLine("  </defs>")
        appendLine("  <style>")
        // Every rule sits in a media query: SVGO (which Docusaurus runs over SVGs imported into MDX) inlines rules outside
        // media queries, or in `screen` ones, into style attributes. Inlining the palette drops the root class every other
        // rule depends on, after which SVGO deletes them all as unused.
        appendLine("    @media all {")
        appendLine("      .$ROOT_CLASS {")
        PALETTE.forEach { (name, value) -> appendLine("        --uml-$name: var(${value.themeVariable}, ${value.fallback});") }
        appendLine("      }")
        appendLine("      .$ROOT_CLASS text { font-family: ${cssFontFamily()}; fill: var(--uml-text); }")
        appendLine("      .$ROOT_CLASS .box { fill: var(--uml-box); stroke: var(--uml-border); stroke-width: 1; }")
        appendLine("      .$ROOT_CLASS .header { fill: var(--uml-header); }")
        appendLine("      .$ROOT_CLASS .zbex .header { fill: var(--uml-zbex-header); }")
        appendLine("      .$ROOT_CLASS .outline { fill: none; stroke: var(--uml-border); stroke-width: 1; }")
        appendLine("      .$ROOT_CLASS .separator { stroke: var(--uml-border); stroke-width: 1; }")
        appendLine("      .$ROOT_CLASS .name { font-size: ${NAME_FONT_SIZE}px; font-weight: bold; }")
        appendLine("      .$ROOT_CLASS .stereotype, .$ROOT_CLASS .parent { font-size: ${STEREOTYPE_FONT_SIZE}px; font-style: italic; }")
        appendLine("      .$ROOT_CLASS .zbex .name, .$ROOT_CLASS .zbex .stereotype, .$ROOT_CLASS .zbex .parent { fill: var(--uml-zbex-header-text); }")
        appendLine("      .$ROOT_CLASS .attribute { font-size: ${ATTRIBUTE_FONT_SIZE}px; }")
        appendLine("      .$ROOT_CLASS .zbex-row { fill: var(--uml-zbex-row); }")
        appendLine("      .$ROOT_CLASS .zbex-marker { font-size: ${STEREOTYPE_FONT_SIZE}px; font-style: italic; font-weight: bold; fill: var(--uml-zbex-marker); }")
        appendLine("      .$ROOT_CLASS .edge { fill: none; stroke: var(--uml-line); stroke-width: 1.2; }")
        appendLine("      .$ROOT_CLASS .arrow { fill: var(--uml-box); stroke: var(--uml-line); stroke-width: 1; }")
        appendLine("      .$ROOT_CLASS .label { font-size: ${LABEL_FONT_SIZE}px; fill: var(--uml-label); }")
        // The links the page adds around the class boxes. Selected by [href] rather than tag, as SVGO drops rules naming
        // tags the SVG doesn't contain.
        appendLine("      .$ROOT_CLASS [href] { text-decoration: none; }")
        appendLine("      .$ROOT_CLASS [href]:hover .outline, .$ROOT_CLASS [href]:focus-visible .outline { stroke: var(--uml-highlight); stroke-width: 2; }")
        appendLine("    }")
        appendLine("  </style>")
    }

    private fun StringBuilder.appendEdge(edge: PositionedEdge, hops: List<List<LineHops.Hop>>, drawing: SharedLines.Drawing) {
        // Leave out edges entirely drawn by the edges before them (see [SharedLines]).
        if (edge.points.size < 2 || drawing.isEmpty)
            return

        // A path rather than a polyline, so lines can hop over the lines they cross.
        val marker = if (edge.kind == EdgeKind.GENERALISATION && drawing.marker) """ marker-end="url(#uml-generalisation)"""" else ""
        appendLine(
            """    <path class="edge ${edge.kind.name.lowercase()}" data-source="${esc(edge.source)}" data-target="${esc(edge.target)}" """ +
                """d="${LineHops.path(edge.points, hops, drawing.visible)}"$marker/>"""
        )
    }

    private fun StringBuilder.appendNode(positioned: PositionedNode) {
        val node = positioned.sized.node
        val x = positioned.x
        val y = positioned.y
        val w = positioned.width
        val h = positioned.height
        val headerHeight = positioned.sized.headerHeight
        val cx = x + w / 2

        val indent = "    "
        val href = if (links) """ $HREF_ATTRIBUTE="${esc(node.url)}"""" else ""
        appendLine("""$indent<g class="class${if (node.isZbex) " zbex" else ""}" data-name="${esc(node.name)}"$href>""")

        val inner = "$indent  "
        appendLine(inner + rect("box", x, y, w, h, rounded = true))
        val titleOnly = node.rows.isEmpty()
        if (titleOnly)
            appendLine(inner + rect("header", x, y, w, h, rounded = true))
        else
            appendLine("""$inner<path class="header" d="${roundedTop(x, y, w, headerHeight)}"/>""")

        // Centre the stereotypes and name within the header (which is the whole box for title only classes).
        var lineTop = y + (headerHeight - positioned.sized.titleHeight) / 2
        node.parentLabel?.let {
            appendLine(
                """$inner<text class="parent" x="${svgNumber(x + w - PARENT_PADDING_X)}" """ +
                    """y="${svgNumber(baseline(lineTop, STEREOTYPE_LINE_HEIGHT, STEREOTYPE_FONT_SIZE))}" text-anchor="end">${esc(it)}</text>"""
            )
            lineTop += STEREOTYPE_LINE_HEIGHT
        }
        node.stereotypes.forEach {
            appendLine(
                """$inner<text class="stereotype" x="${svgNumber(cx)}" y="${svgNumber(baseline(lineTop, STEREOTYPE_LINE_HEIGHT, STEREOTYPE_FONT_SIZE))}" """ +
                    """text-anchor="middle">${esc("«$it»")}</text>"""
            )
            lineTop += STEREOTYPE_LINE_HEIGHT
        }
        appendLine(
            """$inner<text class="name" x="${svgNumber(cx)}" y="${svgNumber(baseline(lineTop, NAME_LINE_HEIGHT, NAME_FONT_SIZE))}" """ +
                """text-anchor="middle">${esc(node.name)}</text>"""
        )

        val separatorY = y + headerHeight
        if (!titleOnly)
            appendLine("""$inner<line class="separator" x1="${svgNumber(x)}" y1="${svgNumber(separatorY)}" x2="${svgNumber(x + w)}" y2="${svgNumber(separatorY)}"/>""")

        var rowTop = separatorY + BODY_PADDING_Y
        node.rows.forEach { row ->
            val rowBaseline = svgNumber(baseline(rowTop, ROW_HEIGHT, ATTRIBUTE_FONT_SIZE))
            if (row.isZbex)
                appendLine(inner + rect("zbex-row", x + 1, rowTop, w - 2, ROW_HEIGHT))
            appendLine("""$inner<text class="attribute" x="${svgNumber(x + PADDING_X)}" y="$rowBaseline">${esc(row.text)}</text>""")
            if (row.isZbex)
                appendLine(
                    """$inner<text class="zbex-marker" x="${svgNumber(x + w - PADDING_X)}" y="$rowBaseline" text-anchor="end">""" +
                        """${esc(ZBEX_ROW_MARKER)}</text>"""
                )
            rowTop += ROW_HEIGHT
        }

        // Redraw the outline last so the header and row tints don't cover it.
        appendLine(inner + rect("outline", x, y, w, h, rounded = true))

        appendLine("$indent</g>")
    }

    private fun StringBuilder.appendLabel(label: PositionedLabel) {
        val x = if (label.alignEnd) label.x + label.width else label.x
        val anchor = if (label.alignEnd) """ text-anchor="end"""" else ""
        appendLine(
            """    <text class="label" x="${svgNumber(x)}" y="${svgNumber(baseline(label.y, label.height, LABEL_FONT_SIZE))}"$anchor>""" +
                """${esc(label.text)}</text>"""
        )
    }

    private fun rect(cssClass: String, x: Double, y: Double, w: Double, h: Double, rounded: Boolean = false): String {
        val corners = if (rounded) """ rx="$BOX_CORNER_RADIUS"""" else ""
        return """<rect class="$cssClass" x="${svgNumber(x)}" y="${svgNumber(y)}" width="${svgNumber(w)}" height="${svgNumber(h)}"$corners/>"""
    }

    private fun roundedTop(x: Double, y: Double, w: Double, h: Double): String {
        val r = BOX_CORNER_RADIUS.toDouble()
        return "M${svgNumber(x)},${svgNumber(y + h)} V${svgNumber(y + r)} Q${svgNumber(x)},${svgNumber(y)} ${svgNumber(x + r)},${svgNumber(y)} " +
            "H${svgNumber(x + w - r)} Q${svgNumber(x + w)},${svgNumber(y)} ${svgNumber(x + w)},${svgNumber(y + r)} V${svgNumber(y + h)} Z"
    }

    private fun cssFontFamily(): String =
        listOf(fontFamily, "DejaVu Sans", "Verdana", "Arial")
            .distinct()
            .joinToString(", ") { "'${it.replace("'", "")}'" } + ", sans-serif"

    /** The baseline that vertically centres text of [fontSize] in a line of [lineHeight] starting at [top]. */
    private fun baseline(top: Double, lineHeight: Double, fontSize: Float) = top + lineHeight / 2 + fontSize * 0.35

    companion object {

        /**
         * Class on the root `<svg>` element. Every style rule is scoped under it, so a diagram inlined into a page doesn't
         * restyle the rest of the page.
         */
        const val ROOT_CLASS = "uml-diagram"

        /** Attribute holding the site relative path of a class box's documentation page, e.g. `/ewb/IEC61970/Base/Wires/Breaker`. */
        const val HREF_ATTRIBUTE = "data-uml-href"

        fun esc(text: String): String = buildString(text.length) {
            text.forEach {
                when (it) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(it)
                }
            }
        }

    }

}
