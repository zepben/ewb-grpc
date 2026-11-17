/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.commands

import com.zepben.profilemanager.exporters.diagrams.render.SvgRenderer
import org.w3c.dom.Element
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the whole pipeline against the real ewb spec, with test diagram definitions.
 */
class GenerateTest {

    private val spec = File("../spec")

    @Test
    fun `generates class pages and diagrams`() {
        val temp = Files.createTempDirectory("diagrams").toFile()
        try {
            // Use a copy of the spec so the test controls the profile's diagram definitions.
            val specCopy = temp.resolve("spec")
            spec.resolve("ewb").copyRecursively(specCopy.resolve("ewb"))
            specCopy.resolve("ewb/__diagrams.yml").apply {
                writeText(
                    """
                    diagrams:
                      - path: IEC61970/Base/Core/Names
                        classes: [IdentifiedObject, Name, NameType]
                      - path: IEC61970/Base/Wires/Switches
                        name: Switching
                        classes: [ConductingEquipment, Switch, ProtectedSwitch, Breaker, Terminal, PhaseCode]
                    """.trimIndent()
                )
            }
            val out = temp.resolve("site")

            Generate().parse(listOf(specCopy.path, out.path, "--profiles", "ewb"))

            assertDiagram(
                out.resolve("ewb/IEC61970/Base/Core/Names.svg"),
                classes = listOf("IdentifiedObject", "Name", "NameType"),
                associations = 2,
                generalisations = 0,
            )
            assertDiagram(
                out.resolve("ewb/IEC61970/Base/Wires/Switches.svg"),
                classes = listOf("ConductingEquipment", "Switch", "ProtectedSwitch", "Breaker", "Terminal", "PhaseCode"),
                associations = 1,
                generalisations = 3,
            )

            val namesPage = out.resolve("ewb/IEC61970/Base/Core/Names.mdx").readText()
            assertTrue(namesPage.contains("import Diagram from './Names.svg';"), namesPage)
            assertTrue(namesPage.startsWith("---\ntitle: Names"), "Front matter must come first: $namesPage")
            assertTrue(namesPage.contains("import UmlDiagram from '@site/src/components/UmlDiagram';"), namesPage)
            assertTrue(
                namesPage.contains("\n<UmlDiagram svg={Diagram} hrefAttribute='data-uml-href' />\n"),
                "The page should link the boxes the SVG marks: $namesPage",
            )
            assertTrue(namesPage.contains("[View as SVG](./Names.svg)"), namesPage)
            assertTrue(!namesPage.contains("data-uml-link"), namesPage)
            val switchesPage = out.resolve("ewb/IEC61970/Base/Wires/Switches.mdx").readText()
            assertTrue(switchesPage.contains("### Switching - *UML Class Diagram*"), switchesPage)
            // The slug is the file name, not the display name, so the page's URL matches the links to it.
            assertTrue(switchesPage.contains("\nslug: Switches\n"), switchesPage)
            assertTrue(!out.resolve("images").exists(), "Diagrams should sit next to their pages, not in an images folder")

            // Class pages list the diagrams their class is drawn in.
            val identifiedObject = out.resolve("ewb/IEC61970/Base/Core/IdentifiedObject.mdx").readText()
            assertTrue(
                identifiedObject.contains("## UML Diagrams\n\n- <Link to='/ewb/IEC61970/Base/Core/Names'>Names</Link> (IEC61970/Base/Core)\n"),
                identifiedObject,
            )
            val breaker = out.resolve("ewb/IEC61970/Base/Wires/Breaker.mdx").readText()
            assertTrue(breaker.contains("- <Link to='/ewb/IEC61970/Base/Wires/Switches'>Switching</Link> (IEC61970/Base/Wires)"), breaker)
            val baseVoltage = out.resolve("ewb/IEC61970/Base/Core/BaseVoltage.mdx").readText()
            assertTrue(!baseVoltage.contains("UML Diagrams"), "BaseVoltage isn't in any diagram: $baseVoltage")
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun assertDiagram(svg: File, classes: List<String>, associations: Int, generalisations: Int) {
        // Throws if the SVG isn't well formed XML.
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg)

        val texts = doc.getElementsByTagName("text").elements().map { it.textContent }
        classes.forEach { assertTrue(it in texts, "Expected '$it' in ${svg.name}") }

        // The SVG has no links, just the path for the page to link each class box to.
        assertEquals(0, doc.getElementsByTagName("a").length, "Links in ${svg.name}")
        val hrefs = doc.getElementsByTagName("g").elements()
            .filter { "class" in it.getAttribute("class").split(" ") }
            .associate { it.getAttribute("data-name") to it.getAttribute(SvgRenderer.HREF_ATTRIBUTE) }
        assertEquals(classes.sorted(), hrefs.keys.sorted(), "Classes in ${svg.name}")
        assertTrue(hrefs.all { (name, href) -> href.startsWith("/ewb/") && href.endsWith("/$name") }, "Hrefs in ${svg.name}: $hrefs")

        val polylines = doc.getElementsByTagName("path").elements().map { it.getAttribute("class") }.filter { it.startsWith("edge ") }
        assertEquals(associations, polylines.count { it.contains("association") }, "Associations in ${svg.name}")
        assertEquals(generalisations, polylines.count { it.contains("generalisation") }, "Generalisations in ${svg.name}")
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

}
