package com.zepben.profilemanager.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.zepben.profilemanager.exporters.YamlFileSystem
import com.zepben.profilemanager.importers.EnterpriseArchitectDatabase

class Import : CliktCommand() {
    private val pathToDatabase by argument("Database Path").file(mustExist = true)
    private val outputPath by argument("Output path").file(mustExist = true, canBeFile = false)
    private val profile by option(help = "Profile to export").choice("ewb", "TC57CIM").default("ewb")

    override fun run() {
        val root = EnterpriseArchitectDatabase(pathToDatabase).import(profile)
        YamlFileSystem(outputPath).export(root)
    }
}
