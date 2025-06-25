package com.zepben.profilemanager.importers

import com.charleskorn.kaml.Yaml
import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Element
import com.zepben.profilemanager.models.Package
import com.zepben.profilemanager.models.Root
import kotlinx.serialization.decodeFromString
import java.io.File
import java.io.FileNotFoundException

class YamlFileSystem(inputSource: File) : Importer(inputSource) {

    override fun import(profile: String): Element {
        val root = Root(0, profile)
        importInternal(root, File(inputSource.absolutePath + File.separator + profile))
        root.reindex()
        return root
    }

    private fun importInternal(root: Element, path: File) {
        path.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val p = packageInfo(file)
                root.addChild(p)
                importInternal(p, file)
            } else {
                if (file.name == "__metadata.yaml") {
                    return@forEach
                }
                kotlin.runCatching {
                    val c = Yaml.default.decodeFromString<Class>(file.readText())
                    root.addChild(c)
                }.onFailure { t ->
                    println("Failed to process ${file.specRelativePath}: ${t.message}")
                    throw t
                }
            }
        }

    }

    private fun packageInfo(path: File): Package {
        try {
            val f = File(path.absolutePath + File.separator + "__metadata.yaml")
            return Yaml.default.decodeFromString<Package>(f.readText())
        } catch (e: FileNotFoundException) {
            return Package()
        } catch (t: Throwable) {
            println("Failed to read package infor for ${path.specRelativePath}: ${t.message}")
            throw t
        }
    }

    private val File.specRelativePath get() = toRelativeString(inputSource.absoluteFile)

}
