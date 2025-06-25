package com.zepben.profilemanager.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.zepben.profilemanager.exporters.Docusaurus2Mdx
import com.zepben.profilemanager.importers.YamlFileSystem
import java.io.File
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

class Generate : CliktCommand() {
    private val inputModel by argument("Input path").file(mustExist = true, canBeFile = false)
    private val outputPath by argument("Output path").file(mustExist = false, canBeFile = false)
    private val profiles by option(help = "Profile to export").multiple(default = listOf("ewb", "TC57CIM"))
    private val deleteOutput by option(help = "Indicates the previous output should be deleted before generating the new profiles").flag("--no-delete-output", default = true)

    private val exportPathOverride: Map<String, String> = mapOf(
        "ewb" to "",
        "TC57CIM" to "cim100"
    )

    override fun run() {
        if (deleteOutput)
            outputPath.deleteContents()

        profiles.forEach {
            print("Generating $it profile...")
            val root = YamlFileSystem(inputModel).import(it)
            val resolvedOutputPath = outputPath.resolve(File(exportPathOverride.getOrDefault(it, "")))
            resolvedOutputPath.mkdirs()
            Docusaurus2Mdx(
                resolvedOutputPath,
                exportPathOverride.getOrDefault(it, ""),
                validateAncestors = it == "ewb"
            ).export(root)
            println("done.")
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
