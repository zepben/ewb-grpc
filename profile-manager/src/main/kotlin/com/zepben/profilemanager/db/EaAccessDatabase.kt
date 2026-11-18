package com.zepben.profilemanager.db

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class EaAccessDatabase private constructor(private val connection: Connection) {
    companion object {

        /**
         * Jackcess reads text in Jet 3 (Access 97) files, the format of many EA `.eap` files, using the platform
         * charset unless told otherwise, which mangles any non-ASCII text EA wrote as Windows-1252.
         */
        private const val JET3_CHARSET_PROPERTY = "com.healthmarketscience.jackcess.charset.VERSION_3"

        fun connect(db: File): EaAccessDatabase {
            if (System.getProperty(JET3_CHARSET_PROPERTY) == null)
                System.setProperty(JET3_CHARSET_PROPERTY, "windows-1252")

            val firstException = mutableListOf<Throwable>()
            val conn = runCatching { DriverManager.getConnection("jdbc:ucanaccess://${db.absolutePath}") }
                .recoverCatching {
                    firstException.add(it)
                    DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}")
                }
                .getOrElse {
                    firstException.first().printStackTrace()
                    throw IllegalArgumentException("Unable to open database '${db.absolutePath}'", it)
                }
            return EaAccessDatabase(conn)
        }
    }

    fun getPackageByName(name: String): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_package WHERE Name = ? ORDER BY Package_ID DESC",
        )
        statement.setString(1, name)
        return statement.executeQuery()
    }

    fun getPackageById(id: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_package WHERE Package_ID = ? ORDER BY Package_ID",
        )
        statement.setInt(1, id)
        return statement.executeQuery()
    }

    fun getPackageByParentId(id: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_package WHERE Parent_ID = ? ORDER BY Package_ID",
        )
        statement.setInt(1, id)
        return statement.executeQuery()
    }

    fun getClassesByParentPackageId(id: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_object WHERE Package_ID = ? AND Object_Type = 'Class' ORDER BY Object_ID",
        )
        statement.setInt(1, id)
        return statement.executeQuery()
    }

    fun getAttributesByObjectId(id: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_attribute WHERE Object_ID = ? ORDER BY ID",
        )
        statement.setInt(1, id)
        return statement.executeQuery()
    }

    fun getConnectorsByObjectId(id: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_connector WHERE Start_Object_ID = ? OR End_Object_ID = ? ORDER BY Connector_ID",
        )
        statement.setInt(1, id)
        statement.setInt(2, id)
        return statement.executeQuery()
    }

    fun getDiagramsByPackageId(id: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT Diagram_ID, Name FROM t_diagram WHERE Package_ID = ? ORDER BY Diagram_ID",
        )
        statement.setInt(1, id)
        return statement.executeQuery()
    }

    fun getDiagramObjectIds(diagramId: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT Object_ID FROM t_diagramobjects WHERE Diagram_ID = ?",
        )
        statement.setInt(1, diagramId)
        return statement.executeQuery()
    }

    /**
     * The connectors between two elements that are both on the diagram, with whether the diagram hides each one. EA
     * draws a connector between two elements on a diagram unless the diagram's link to it is hidden, so connectors
     * without a link are drawn (and have a null `Hidden`).
     */
    fun getDiagramConnectors(diagramId: Int): ResultSet {
        val statement = connection.prepareStatement(
            """
            SELECT c.Connector_ID, c.Start_Object_ID, c.End_Object_ID, c.SourceCard, c.DestCard, dl.Hidden
            FROM t_connector c
            LEFT JOIN t_diagramlinks dl ON dl.ConnectorID = c.Connector_ID AND dl.DiagramID = ?
            WHERE c.Start_Object_ID IN (SELECT Object_ID FROM t_diagramobjects WHERE Diagram_ID = ?)
              AND c.End_Object_ID IN (SELECT Object_ID FROM t_diagramobjects WHERE Diagram_ID = ?)
            ORDER BY c.Connector_ID
            """.trimIndent(),
        )
        statement.setInt(1, diagramId)
        statement.setInt(2, diagramId)
        statement.setInt(3, diagramId)
        return statement.executeQuery()
    }
}
