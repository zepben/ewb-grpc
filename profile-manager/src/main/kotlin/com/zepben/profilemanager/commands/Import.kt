package com.zepben.profilemanager.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.zepben.profilemanager.exporters.YamlFileSystem
import com.zepben.profilemanager.importers.EnterpriseArchitectDatabase

class Import : CliktCommand() {

    private val pathToDatabase by argument("Database Path").file(mustExist = true)
    private val outputPath by argument("Output path").file(canBeFile = false)
    /**
     * CIM is the new name for TC57CIM as per https://cim-mg.ucaiug.io/1.1/section5-cim-uml-modeling-rules-and-recommendations/
     *
     * Sub packages for each working group have also been renamed:
     * * WG13 - Grid (formerly IEC61970) package,
     * * WG14 - Enterprise (formerly IEC61968) package,
     * * WG16 - Market (formerly IEC62325) package.
     */
    private val profile by option(help = "Profile to export").default("CIM")

    override fun run() {
        println("Verifying output directory exists (or creating it)...")
        require(outputPath.exists() || outputPath.mkdirs()) { "Unable to create the output path '$outputPath'" }
        println("Loading $profile from EnterpriseArchitect database '$pathToDatabase'...")
        val root = EnterpriseArchitectDatabase(pathToDatabase).import(profile)
        println("Exporting YAML spec...")
        YamlFileSystem(outputPath).export(root)
        println("Done.")
    }
}
