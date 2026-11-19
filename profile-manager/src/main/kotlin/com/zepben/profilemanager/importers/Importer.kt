package com.zepben.profilemanager.importers

import com.zepben.profilemanager.models.Root
import java.io.File

abstract class Importer(val inputSource: File) {
    abstract fun import(profile: String): Root
}
