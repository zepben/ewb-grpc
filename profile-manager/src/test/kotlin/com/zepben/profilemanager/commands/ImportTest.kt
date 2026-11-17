/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.commands

import com.zepben.profilemanager.exporters.diagrams.model.DiagramClass
import com.zepben.profilemanager.exporters.diagrams.model.DiagramContent
import com.zepben.profilemanager.exporters.diagrams.model.DiagramDefinition
import com.zepben.profilemanager.exporters.diagrams.model.DiagramFile
import com.zepben.profilemanager.importers.uniqueSlug
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Imports a tiny EA database, built as sqlite, to check the diagram definitions it writes.
 */
class ImportTest {

    private val temp = Files.createTempDirectory("import").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun `writes the diagram definitions`() {
        val db = temp.resolve("model.sqlite3")
        createDatabase(db)
        val out = temp.resolve("spec")

        Import().parse(listOf(db.path, out.path, "--profile", "CIM"))

        val file = out.resolve("CIM/${DiagramFile.FILE_NAME}")
        assertTrue(file.isFile)
        assertTrue(out.resolve("CIM/Grid/Core/Terminal.yaml").isFile)

        val text = file.readText()
        assertTrue("\n\n  - path:" in text || "\n\n- path:" in text, "Diagrams should be separated by blank lines:\n$text")

        assertEquals(
            DiagramFile(
                listOf(
                    DiagramDefinition(
                        "Grid/Core/CoreInheritance",
                        show = DiagramContent.INHERITANCE,
                        classes = listOf(DiagramClass("ConductingEquipment"), DiagramClass("Equipment"), DiagramClass("Terminal")),
                    ),
                    DiagramDefinition(
                        "Grid/Core/Main",
                        classes = listOf(DiagramClass("ConductingEquipment"), DiagramClass("Equipment"), DiagramClass("Terminal")),
                    ),
                    DiagramDefinition(
                        "Grid/Core/Main_2",
                        name = "Ma in",
                        show = DiagramContent.RELATIONSHIPS,
                        classes = listOf(DiagramClass("ConductingEquipment"), DiagramClass("Equipment"), DiagramClass("Terminal")),
                    ),
                    DiagramDefinition(
                        "Grid/Core/Overrides",
                        classes = listOf(
                            DiagramClass("ConductingEquipment"),
                            DiagramClass("Equipment", DiagramContent.INHERITANCE),
                            DiagramClass("Switch", DiagramContent.INHERITANCE),
                            DiagramClass("Terminal"),
                        ),
                    ),
                ),
            ),
            DiagramFile.parse(text),
        )
    }

    @Test
    fun `slugs diagram names`() {
        assertEquals("FieldDispatchHistory", uniqueSlug("Field Dispatch History", emptySet()))
        assertEquals("600-1EU_extension", uniqueSlug("600-1 EU_extension", emptySet()))
        assertEquals("Diagram", uniqueSlug("???", emptySet()))
        assertEquals("Main_3", uniqueSlug("Ma in", setOf("Main", "Main_2")))
    }

    private fun createDatabase(file: File) {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { conn ->
            conn.createStatement().use { s ->
                listOf(
                    "CREATE TABLE t_package (Package_ID INTEGER, Name TEXT, Parent_ID INTEGER, Notes TEXT)",
                    "CREATE TABLE t_object (Object_ID INTEGER, Name TEXT, Object_Type TEXT, Package_ID INTEGER, Note TEXT)",
                    "CREATE TABLE t_attribute (Object_ID INTEGER, Name TEXT, Type TEXT, Notes TEXT)",
                    """CREATE TABLE t_connector (Connector_ID INTEGER, Start_Object_ID INTEGER, End_Object_ID INTEGER, Connector_Type TEXT,
                       SourceCard TEXT, DestCard TEXT, SourceRole TEXT, DestRole TEXT, SourceRoleNote TEXT, DestRoleNote TEXT)""",
                    "CREATE TABLE t_diagram (Diagram_ID INTEGER, Package_ID INTEGER, Name TEXT)",
                    "CREATE TABLE t_diagramobjects (Diagram_ID INTEGER, Object_ID INTEGER)",
                    "CREATE TABLE t_diagramlinks (DiagramID INTEGER, ConnectorID INTEGER, Hidden INTEGER)",

                    "INSERT INTO t_package VALUES (1, 'Model', 0, NULL), (2, 'CIM', 1, NULL), (3, 'Grid', 2, NULL), (4, 'Core', 3, NULL)",
                    """INSERT INTO t_object VALUES (10, 'Equipment', 'Class', 4, NULL), (11, 'ConductingEquipment', 'Class', 4, NULL),
                       (12, 'Terminal', 'Class', 4, NULL), (15, 'Switch', 'Class', 4, NULL), (13, 'A note', 'Note', 4, NULL), (14, 'Outside', 'Class', 1, NULL)""",
                    """INSERT INTO t_connector VALUES
                       (100, 11, 10, 'Generalization', NULL, NULL, NULL, NULL, NULL, NULL),
                       (101, 12, 11, 'Association', '0..*', '0..1', 'Terminals', 'ConductingEquipment', NULL, NULL),
                       (102, 12, 14, 'Association', '0..*', '1', NULL, NULL, NULL, NULL),
                       (103, 15, 11, 'Generalization', NULL, NULL, NULL, NULL, NULL, NULL),
                       (104, 15, 10, 'Association', '0..*', '1', NULL, NULL, NULL, NULL)""",
                    // Main draws everything. Its name clashes with "Ma in" once slugged.
                    // CoreInheritance hides the association, and has no row for the generalisation, so draws it.
                    // "Ma in" hides the generalisation, but draws the association.
                    // NotesOnly has no classes, so is skipped.
                    // Overrides mostly draws both kinds, but hides the only association of Equipment and Switch.
                    """INSERT INTO t_diagram VALUES (1000, 4, 'Main'), (1001, 4, 'CoreInheritance'), (1002, 4, 'Ma in'), (1003, 4, 'NotesOnly'),
                       (1004, 4, 'Overrides')""",
                    """INSERT INTO t_diagramobjects VALUES (1000, 10), (1000, 11), (1000, 12), (1000, 13), (1000, 14),
                       (1001, 10), (1001, 11), (1001, 12), (1002, 10), (1002, 11), (1002, 12), (1003, 13),
                       (1004, 10), (1004, 11), (1004, 12), (1004, 15)""",
                    "INSERT INTO t_diagramlinks VALUES (1000, 100, 0), (1000, 101, 0), (1001, 101, 1), (1002, 100, 1), (1004, 104, 1)",
                ).forEach { s.executeUpdate(it) }
            }
        }
    }

}
