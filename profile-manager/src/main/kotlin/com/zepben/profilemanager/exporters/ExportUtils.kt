package com.zepben.profilemanager.exporters

import java.io.File

fun File.createDirectoryInFolder(name: String): File {
    val path = this.absolutePath + File.separator + name
    val file = File(path)
    file.mkdir()
    return file
}

fun File.fileInFolder(name: String, extension: String): File {
    val path = this.absolutePath + File.separator + name + extension
    val file = File(path)
    return file
}
