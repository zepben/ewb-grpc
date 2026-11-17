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
        file.writeText(result)
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
        file.writeText(result)
    }

    private fun writeMetadata(node: Package, newLocation: File) {
        val file = newLocation.fileInFolder("__metadata", ".yaml")
        val result = yamlEncoder.encodeToString(node)
        file.writeText(result)
    }
}
