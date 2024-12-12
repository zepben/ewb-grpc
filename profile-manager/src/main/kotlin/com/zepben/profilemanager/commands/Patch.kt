package com.zepben.profilemanager.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.zepben.profilemanager.exporters.YamlFileSystem
import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Package
import com.zepben.profilemanager.models.Root
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.forEachDirectoryEntry
import kotlin.streams.toList

class Patch : CliktCommand() {
    private val profilePath by argument("Profile path").file(mustExist = true, canBeFile = false)
    private val profile by option(help = "Profile to export").choice("ewb", "TC57CIM").default("ewb")

    override fun run() {
        val zepbenRoot = com.zepben.profilemanager.importers.YamlFileSystem(profilePath).import(profile) as Root
        val cimRoot = com.zepben.profilemanager.importers.YamlFileSystem(profilePath).import("TC57CIM") as Root

        val zepbenLookup = mutableMapOf<String, Class>()
        val cimLookup = mutableMapOf<String, Class>()

        zepbenRoot.iterateWithState(
            classCallback = { elem: Class, s: String ->
                val path = s + "." + elem.name
                zepbenLookup[path] = elem
            },
            packageCallback = { p: Package, s: String ->
                arrayOf(s, p.name).filter { it.isNotBlank() }.joinToString(".")
            },
            initialState = ""
        )

        cimRoot.iterateWithState(
            classCallback = { elem: Class, s: String ->
                val path = s + "." + elem.name
                cimLookup[path] = elem
            },
            packageCallback = { p: Package, s: String ->
                arrayOf(s, p.name).filter { it.isNotBlank() }.joinToString(".")
            },
            initialState = ""
        )

        println("Diffing between CIM and Zepben Profiles")
        println("In Zepben, but not CIM")
        val inZepbenDiff = zepbenLookup.keys - cimLookup.keys
        inZepbenDiff.forEach {
            println(it)
        }

        val grpcPath = Path("/home/zarakay/dev/evolve-grpc/proto/zepben/protobuf/cim")
        val sdkPath = Path("/home/zarakay/dev/evolve-sdk-jvm/src/main/kotlin/com/zepben/evolve/cim")

        val grpcItems = Files.walk(grpcPath)
            .filter { it.toFile().extension == "proto" }
            .map { grpcPath.relativize(it) }
            .map { it.toString().removeSuffix(".proto").split("/").joinToString(".").lowercase() }
            .toList()

        val sdkItems = Files.walk(sdkPath)
            .filter { it.toFile().extension == "kt" }
            .map { sdkPath.relativize(it) }
            .map { it.toString().removeSuffix(".kt").split("/").joinToString(".").lowercase() }
            .toList()

        val zepbenItems = zepbenLookup.keys.map { it.lowercase().removePrefix("extensions.") }

        println("==== Diffing GRPC ====")
        println("---- In Grpc Not Zepben ----")
        val inGrpcDiff = grpcItems - zepbenItems
        inGrpcDiff.forEach {
            println(it)
        }

        println("---- In Zepben but not Grpc ----")
        val inZepbenNoGrpc = zepbenItems - grpcItems
        inZepbenNoGrpc.forEach {
            println(it)
        }

        println("==== Diffing SDK ====")
        println("---- In SDK Not Zepben ----")
        val inSdkDiff = sdkItems - zepbenItems
        inSdkDiff.forEach {
            println(it)
        }

        println("---- In Zepben but not SDK ----")
        val inZepbenNoSdk = zepbenItems - sdkItems
        inZepbenNoSdk.forEach {
            println(it)
        }



//        YamlFileSystem(profilePath).export(zepbenRoot)
    }
}
