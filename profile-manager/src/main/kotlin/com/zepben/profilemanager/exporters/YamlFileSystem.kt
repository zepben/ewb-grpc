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
            breakScalarsAt = 120,
            singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
            multiLineStringStyle = MultiLineStringStyle.Literal,

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
        val result = yamlEncoder.encodeToString(node)
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

/** A line starting a block scalar, e.g. `  description: |-`, whose content is the following, more indented, lines. */
private val blockScalarStart = Regex("""^\s*(?:- )?[A-Za-z_]\w*: [|>][-+]?$""")

/**
 * Tidy YAML written by kaml for writing to a file: quote values YAML 1.1 parsers would read as booleans, and end it
 * with a single newline. Lines inside block scalars are text, so are left alone.
 */
internal fun finishYaml(yaml: String): String {
    var blockIndent: Int? = null
    return yaml.trimEnd('\n').lines().joinToString("\n", postfix = "\n") { line ->
        val indent = line.length - line.trimStart().length
        if (blockIndent != null && (line.isBlank() || indent > blockIndent!!))
            return@joinToString line

        blockIndent = if (blockScalarStart.matches(line)) indent else null
        line.replace(yaml11Boolean, "$1\"$2\"")
    }
}
