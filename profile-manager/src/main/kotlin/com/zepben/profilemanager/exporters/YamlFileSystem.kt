package com.zepben.profilemanager.exporters

import com.charleskorn.kaml.*
import com.zepben.profilemanager.exporters.diagrams.model.DiagramFile
import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Element
import com.zepben.profilemanager.models.Root
import com.zepben.profilemanager.models.Package
import kotlinx.serialization.encodeToString
import java.io.File

class YamlFileSystem(exportLocation: File) : Exporter(exportLocation) {

    private val yamlEncoder: Yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
            encodeDefaults = false,
            breakScalarsAt = Int.MAX_VALUE,
            encodingIndentationSize = 2,
            sequenceBlockIndent = 2,
            singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
            multiLineStringStyle = MultiLineStringStyle.Literal,
            ambiguousQuoteStyle = AmbiguousQuoteStyle.DoubleQuoted,
        )
    )

    override fun export(e: Element) {
        exportInternal(e, exportLocation)
    }

    /**
     * Write [diagrams] as the diagram definitions file of the profile [root] exported by [export].
     */
    fun exportDiagrams(root: Root, diagrams: DiagramFile) {
        val file = exportLocation.createDirectoryInFolder(root.name).resolve(DiagramFile.FILE_NAME)
        // Separate the diagrams with a blank line, to make the file easier to read.
        val result = yamlEncoder.encodeToString(diagrams)
            .replace(diagramStart, "\n\n")
            .replaceFirst(":\n\n", ":\n")
        file.writeText(finishYaml(result))
    }

    private fun exportInternal(node: Element, exportLocation: File) {
        when(node) {
            is Package -> {
                val newLocation = exportLocation.createDirectoryInFolder(node.name)
                writeMetadata(node, newLocation)
                node.children.forEach { exportInternal(it, newLocation) }
            }
            is Class -> {
                writeClass(node, exportLocation)
            }
            is Root -> {
                val newLocation = exportLocation.createDirectoryInFolder(node.name)
                node.children.forEach { exportInternal(it, newLocation) }
            }
        }
    }

    private val diagramStart = Regex("\n(?= *- path: )")

    private fun writeClass(node: Class, exportLocation: File) {
        val file = exportLocation.fileInFolder(node.name, ".yaml")
        val result = yamlEncoder.encodeToString(node.sorted())
        file.writeText(finishYaml(result))
    }

    private fun writeMetadata(node: Package, newLocation: File) {
        val file = newLocation.fileInFolder("__metadata", ".yaml")
        val result = yamlEncoder.encodeToString(node)
        file.writeText(finishYaml(result))
    }
}

/**
 * A plain scalar YAML 1.1 parsers, such as PyYAML used by code-gen and cim-ple, read as a boolean, as the whole value
 * after a key or list item. kaml only quotes values that are ambiguous in YAML 1.2.
 */
private val yaml11Boolean = Regex("""^(\s*(?:- )?[A-Za-z_]\w*: |\s*- )(yes|Yes|YES|no|No|NO|on|On|ON|off|Off|OFF)$""")

/**
 * A single-quoted scalar taking up the rest of the line, after a key or list item. A `'` inside it is escaped as `''`.
 */
private val singleQuoted = Regex("""^(\s*(?:- )?[A-Za-z_]\w*: |\s*- )'((?:[^']|'')*)'$""")

/**
 * A value after a key or list item (group 2), indented by group 1, e.g. `  description: |-`. Any following lines
 * indented past the key or `-`, such as a block scalar's content, are part of the value.
 */
private val valueStart = Regex("""^(\s*(?:- )?)([A-Za-z_]\w*: |- )\S""")

/** The column long text is wrapped at. */
private const val WRAP_AT = 120

/**
 * Tidy YAML written by kaml for writing to a file: quote values YAML 1.1 parsers would read as booleans, prefer double
 * quotes to single quotes as Prettier does with `singleQuote: false`, write text that is multi-line or longer than
 * [WRAP_AT] as a folded (`>-`) block scalar wrapped at [WRAP_AT], and end it with a single newline.
 */
internal fun finishYaml(yaml: String): String {
    val lines = yaml.trimEnd('\n').lines()
    var blockIndent: Int? = null
    var skip = 0
    return lines.flatMapIndexed { i, line ->
        val indent = line.length - line.trimStart().length
        if (skip > 0) {
            skip--
            return@flatMapIndexed emptyList()
        }
        if (blockIndent != null && (line.isBlank() || indent > blockIndent!!))
            return@flatMapIndexed listOf(line)

        val start = valueStart.find(line)
        blockIndent = start?.groupValues?.get(1)?.length
        val tidied = line.replace(yaml11Boolean, "$1\"$2\"").replace(singleQuoted) { doubleQuoted(it.groupValues[1], it.groupValues[2]) }
        val folded = start?.let {
            val content = lines.subList(i + 1, lines.size).asSequence()
                .takeWhile { next -> next.isBlank() || next.length - next.trimStart().length > blockIndent!! }
                .toList().dropLastWhile { next -> next.isBlank() }
            folded(it, tidied, content)?.also { skip = content.size }
        }
        folded ?: listOf(tidied)
    }.joinToString("\n", postfix = "\n")
}

/**
 * Rewrite the single-quoted scalar [quoted] after [prefix] in double quotes, as Prettier does. It stays single-quoted
 * if it contains a `"` or `\`, which would need escaping in double quotes.
 */
private fun doubleQuoted(prefix: String, quoted: String): String =
    if ('"' in quoted || '\\' in quoted)
        "$prefix'$quoted'"
    else
        "$prefix\"${quoted.replace("''", "'")}\""

/**
 * The value starting at [start] on [line] and continuing on [content], written as a folded block scalar wrapped at
 * [WRAP_AT], or null if it is single-line text no longer than [WRAP_AT] or can't be written as one without changing
 * it.
 *
 * In a folded block scalar, a line break between two lines of text is read as a space, so each line break in the text
 * is written as an empty line. Lines starting with whitespace are kept as they are, and the line breaks around them are
 * read as written, so aren't doubled.
 */
private fun folded(start: MatchResult, line: String, content: List<String>): List<String>? {
    if (content.isEmpty() && line.length <= WRAP_AT && "\\n" !in line)
        return null
    val column = start.groupValues[1].length
    val value = Yaml.default.parseToYamlNode((listOf(line) + content).joinToString("\n") { it.drop(column) })
    val node = (value as? YamlMap)?.entries?.values?.single() ?: (value as? YamlList)?.items?.single()
    val text = (node as? YamlScalar)?.content ?: return null
    val parts = text.split('\n')
    val unprintable = text.any { it.isISOControl() && it != '\n' && it != '\t' }
    if (content.isEmpty() && line.length <= WRAP_AT && parts.size == 1 || unprintable || text.endsWith('\n') ||
        parts.any { it.isNotEmpty() && it.isBlank() })
        return null

    // A list item's text starting with whitespace would need an indentation indicator relative to the list, so is left.
    val spaced = { part: String -> part.first().isWhitespace() }
    if (start.groupValues[2] == "- " && spaced(text))
        return null

    val pad = " ".repeat(column + 2)
    var previous: String? = null
    val lines = parts.flatMapIndexed { i, part ->
        val gap = i > 0 && (part.isEmpty() || previous == null || !spaced(part) && !spaced(previous!!))
        if (part.isNotEmpty())
            previous = part
        val written = if (part.isEmpty()) emptyList() else if (spaced(part)) listOf(pad + part) else wrap(part, pad)
        if (gap) listOf("") + written else written
    }

    // Text that can't be wrapped, such as a single long word, is left on one line.
    if (content.isEmpty() && parts.size == 1 && lines.size == 1 && lines[0].length > WRAP_AT)
        return null

    // The indentation is taken from the first line of text, so must be given if that line is more indented.
    val indicator = if (spaced(parts.first { it.isNotEmpty() })) "2" else ""
    return listOf("${start.value.dropLast(1)}>$indicator-") + lines
}

/**
 * Wrap [text] into lines starting with [pad], at most [WRAP_AT] long where possible. Lines are only broken at a single
 * space between other characters, which a folded block scalar reads back as that space.
 */
private fun wrap(text: String, pad: String): List<String> {
    val width = WRAP_AT - pad.length
    val lines = mutableListOf<String>()
    var rest = text
    while (rest.length > width) {
        val breaks = (1 until rest.length - 1).filter { rest[it] == ' ' && rest[it - 1] != ' ' && rest[it + 1] != ' ' }
        val at = breaks.lastOrNull { it <= width } ?: breaks.firstOrNull() ?: break
        lines += pad + rest.substring(0, at)
        rest = rest.substring(at + 1)
    }
    lines += pad + rest
    return lines
}
