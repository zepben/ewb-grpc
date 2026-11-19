/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams

import com.zepben.profilemanager.exporters.ClassIndex
import com.zepben.profilemanager.exporters.diagrams.model.DiagramFile
import com.zepben.profilemanager.exporters.diagrams.render.SvgRenderer
import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Package
import com.zepben.profilemanager.models.Root
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagramExporterTest {

    private val temp = Files.createTempDirectory("diagram-exporter").toFile()

    // ewb/Core/{Feeder, Terminal}, so Feeder's page is at /ewb/Core/Feeder.
    private val root = Root(0, "ewb").apply {
        addChild(Package(name = "Core").apply {
            addChild(Class(name = "Feeder"))
            addChild(Class(name = "Terminal"))
        })
    }
    private val classes = ClassIndex.of(root, "")

    private val warnings = mutableListOf<String>()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun export(yaml: String, links: Boolean = true): Map<String, List<DiagramPage>> {
        temp.resolve(DiagramFile.FILE_NAME).writeText(yaml.trimIndent())
        return DiagramExporter(classes, "ewb", "", links, "DejaVu Sans", warn = { warnings += it }).export(temp, temp.resolve("out"))
    }

    @Test
    fun `diagrams are written next to their pages and listed by class`() {
        val pages = export(
            """
            diagrams:
              - path: Core/Overview
                classes: [Feeder, Terminal]
            """
        )

        val page = DiagramPage("Overview", "Core", "/ewb/Core/Overview")
        assertEquals(mapOf("Feeder" to listOf(page), "Terminal" to listOf(page)), pages)
        assertTrue(temp.resolve("out/ewb/Core/Overview.svg").isFile)
        assertTrue(temp.resolve("out/ewb/Core/Overview.mdx").isFile)
        assertEquals(emptyList(), warnings)
    }

    @Test
    fun `a diagram that would replace a class page is skipped`() {
        val pages = export(
            """
            diagrams:
              - path: Core/Feeder
                classes: [Feeder, Terminal]
            """
        )

        assertEquals(emptyMap(), pages)
        assertFalse(temp.resolve("out/ewb/Core/Feeder.mdx").exists())
        assertTrue(warnings.single().contains("would replace the page of a class"), warnings.toString())
    }

    @Test
    fun `only the first of diagrams with the same path is drawn`() {
        val pages = export(
            """
            diagrams:
              - path: Core/Overview
                classes: [Feeder]
              - path: Core/Overview
                classes: [Terminal]
            """
        )

        assertEquals(setOf("Feeder"), pages.keys)
        assertTrue(warnings.single().contains("defined more than once"), warnings.toString())
    }

    @Test
    fun `class boxes are only marked for linking when links are on`() {
        val yaml = """
            diagrams:
              - path: Core/Overview
                classes: [Feeder]
            """
        val svg = File(temp, "out/ewb/Core/Overview.svg")

        export(yaml, links = true)
        assertTrue(svg.readText().contains("""${SvgRenderer.HREF_ATTRIBUTE}="/ewb/Core/Feeder""""))

        export(yaml, links = false)
        assertFalse(svg.readText().contains(SvgRenderer.HREF_ATTRIBUTE))
    }

}
