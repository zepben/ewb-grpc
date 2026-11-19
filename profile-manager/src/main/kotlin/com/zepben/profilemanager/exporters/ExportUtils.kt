package com.zepben.profilemanager.exporters

import java.io.File

fun File.createDirectoryInFolder(name: String): File {
    val path = this.absolutePath + File.separator + name
    val dir = File(path)
    dir.mkdirs()
    return dir
}

fun File.fileInFolder(name: String, extension: String): File {
    val path = this.absolutePath + File.separator + name + extension
    val file = File(path)
    return file
}

/**
 * Joins [segments] into a site relative path, e.g. `sitePath("ewb", "IEC61970/Core")` is `/ewb/IEC61970/Core`, skipping
 * blank segments and stray slashes. No segments gives an empty string, so it can prefix another path.
 */
fun sitePath(vararg segments: String): String =
    segments.map { it.trim('/') }.filter { it.isNotEmpty() }.joinToString("") { "/$it" }

/** How exporters report problems that don't stop the export. */
fun printWarning(message: String) = println("Warning: $message")
