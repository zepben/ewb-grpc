package com.zepben.profilemanager.importers

import com.zepben.profilemanager.models.Element
import java.io.File

abstract class Importer(val inputSource: File) {
    abstract fun import(profile: String): Element
}
