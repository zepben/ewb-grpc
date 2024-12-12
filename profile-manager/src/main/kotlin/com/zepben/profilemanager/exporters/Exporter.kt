package com.zepben.profilemanager.exporters

import com.zepben.profilemanager.models.Element
import java.io.File

abstract class Exporter(val exportLocation: File) {
    abstract fun export(e: Element)
}
