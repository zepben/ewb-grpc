package com.zepben.profilemanager.exporters

import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Element
import com.zepben.profilemanager.models.Package
import com.zepben.profilemanager.models.Root
import java.io.File

class Docusaurus2Mdx(exportLocation: File, private val linkPrefix: String = "") : Exporter(exportLocation) {
    private val linkMap = mutableMapOf<String, String>()

    override fun export(e: Element) {
        val resolvedPrefix = if (linkPrefix.isNotBlank()) "/$linkPrefix" else linkPrefix
        buildLinkMap(e, resolvedPrefix)
        exportInternal(e, exportLocation)
    }

    private fun buildLinkMap(e: Element, path: String = "") {
        if (e is Class) {
            linkMap[e.name] = path + "/" + e.name
        } else {
            e.children.forEach { buildLinkMap(it, path + "/" + e.name) }
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
        return buildString {
            append(addFontmatter(node.name))
            newLine(2)
            append(addDefaultImports())
            newLine(2)
            append(heading("Class Description", 2))
            newLine(2)
            append(node.description.htmlEncodeAngleBrackets())
            newLine(2)
            append(heading("Attributes", 2))
            newLine(2)
            append(attributeTable(node))
            newLine(2)
            append(heading("Relationships", 2))
            newLine(2)
            append(heading("Ancestors", 3))
            newLine(2)
            append(ancestorList(node))
            newLine(2)
            append(heading("descendants", 3))
            newLine(2)
            append(descendantList(node))
            newLine(2)
            append(heading("Associations", 2))
            newLine(2)
            append(associationsTable(node))
            newLine(2)
        }
    }

    private fun associationsTable(node: Class): String {
        if (node.associations().isEmpty()) {
            return "None"
        }

        val headings = listOf("Source Class", "Source Cardinality", "Target", "Target Cardinality",
            "Source Name", "Source Assoc. Description", "Target Name", "Target Assoc. Description")
        val rows = node.associations().map {
            listOf(linkTo(it.source), it.sourceCardinality ?: "", linkTo(it.target),
                it.targetCardinality ?: "", it.sourceName ?: "", it.sourceDescription ?: "", it.targetName ?: "", it.targetDescription ?: "")
        }

        return generateMarkdownTable(headings, rows)
    }

    private fun descendantList(node: Class): String {
        if (node.descendants().isEmpty()) {
            return "No descendant classes"
        }

        return generateMarkdownList(node.descendants().map(::linkTo))
    }

    private fun ancestorList(node: Class): String {
        if (node.ancestors().isEmpty()) {
            return "No ancestor classes"
        }

        return generateMarkdownList(node.ancestors().map(::linkTo))
    }

    private fun attributeTable(node: Class): String {
        if (node.attributes().isEmpty()) {
            return "None"
        }

        val headings = listOf("Name", "Type", "Description")
        val rows = node.attributes().map { listOf(it.name, it.type.let(::linkTo), it.description?.htmlEncodeAngleBrackets().makeMarkdownSafe() ?: "") }

        return generateMarkdownTable(headings, rows)
    }

    private fun heading(text: String, level: Int = 1): String {
        return arrayOf("#".repeat(level), text).joinToString(" ")
    }

    private fun StringBuilder.newLine(n: Int = 1) {
        append("\n".repeat(n))
    }

    private fun addDefaultImports(): String {
        return "import Link from '@docusaurus/Link';"
    }

    private fun addFontmatter(name: String) :String {
        return "---\n" +
        "title: $name\n" +
        "hide_table_of_contents: true\n" +
        "slug: $name\n" +
        "sidebar_position: 0\n" +
        "---"
    }

    private fun generateMarkdownList(list: List<String>): String {
        return list.joinToString("\n") { "- $it" }
    }

    private fun generateMarkdownTable(headings: List<String>, rows: List<List<String>>): String {
        return buildString {
            append("| " + headings.joinToString(" | ") + " |")
            newLine()
            append("| " + headings.map { "---" }.joinToString(" | ") + " |")
            newLine()
            append(rows.joinToString("\n") { "| " + it.joinToString(" | ") + " |" })
        }

    }

    private fun linkTo(name: String?): String {
        if (name == null) return ""

        return if (linkMap.contains(name)) {
            "<Link to='${linkMap[name]}'>$name</Link>"
        } else {
            name
        }
    }
}

private fun String?.htmlEncodeAngleBrackets(): String? {
    return this?.replace("<", "&lt;")?.replace(">", "&gt;")
}

private fun String?.makeMarkdownSafe(): String? {
    return this?.replace("\n", "<br />")?.replace("|", "\\|")
}
