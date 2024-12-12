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

    private fun importInternal(root: Element, inputSource: File) {
        inputSource.listFiles()?.forEach {
            if(it.isDirectory) {
                val p = packageInfo(it)
                root.addChild(p)
                importInternal(p, it)
            } else {
                if (it.name == "__metadata.yaml") {
                  return@forEach
                }
                val c = Yaml.default.decodeFromString<Class>(it.readText())
                root.addChild(c)
            }
        }

    }

    private fun packageInfo(inputSource: File): Package {
        try {
            val f = File(inputSource.absolutePath + File.separator + "__metadata.yaml")
            return Yaml.default.decodeFromString<Package>(f.readText())
        } catch (e: FileNotFoundException) {
            return Package()
        }
    }
}
