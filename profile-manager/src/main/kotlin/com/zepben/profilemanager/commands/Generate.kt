package com.zepben.profilemanager.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.zepben.profilemanager.exporters.ClassIndex
import com.zepben.profilemanager.exporters.Docusaurus2Mdx
import com.zepben.profilemanager.exporters.diagrams.DiagramExporter
import com.zepben.profilemanager.importers.YamlFileSystem
import java.io.File

class Generate : CliktCommand() {
    private val inputModel by argument("Input path").file(mustExist = true, canBeFile = false)
    private val outputPath by argument("Output path").file(mustExist = false, canBeFile = false)
    private val profiles by option(help = "Profile to export").multiple(default = listOf("ewb", "TC57CIM"))
    private val deleteOutput by option(help = "Indicates the previous output should be deleted before generating the new profiles").flag("--no-delete-output", default = true)
    private val noLinks by option(help = "Don't link diagram class boxes to their documentation pages").flag()
    private val fontFamily by option(help = "Font used to measure and render diagram text").default("DejaVu Sans")

    private val exportPathOverride: Map<String, String> = mapOf(
        "ewb" to "",
        "TC57CIM" to "cim100"
    )

    override fun run() {
        if (deleteOutput)
            outputPath.deleteContents()

        profiles.forEach {
            println("Generating $it profile...")
            val root = YamlFileSystem(inputModel).import(it)
            val exportPath = exportPathOverride.getOrDefault(it, "")
            val resolvedOutputPath = outputPath.resolve(File(exportPath))
            resolvedOutputPath.mkdirs()
            val classes = ClassIndex.of(root, exportPath)

            // Draw the diagrams first, so the class pages can link to the diagrams they're in.
            val diagramPages = DiagramExporter(classes, it, exportPath, links = !noLinks, fontFamily)
                .export(inputModel.resolve(it), resolvedOutputPath)

            Docusaurus2Mdx(resolvedOutputPath, classes, validateAncestors = it == "ewb", diagramPages = diagramPages).export(root)
            println("Generated $it profile.")
        }
    }

    fun File.deleteContents() {
        walk().filter { it != this }.toList().forEach { file ->
            if (file.isDirectory())
                file.deleteContents()
            file.delete()
        }
    }

}
