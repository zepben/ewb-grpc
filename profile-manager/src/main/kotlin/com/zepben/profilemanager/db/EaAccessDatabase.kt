package com.zepben.profilemanager.db

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class EaAccessDatabase private constructor(private val connection: Connection) {
    companion object {
        fun connect(db: File): EaAccessDatabase {
            val conn = DriverManager.getConnection("jdbc:ucanaccess://${db.absolutePath}")
            return EaAccessDatabase(conn)
        }
    }

    fun getPackageByName(name: String): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_package WHERE Name = ? ORDER BY Package_ID DESC"
        )
        statement.setString(1, name)
        return statement.executeQuery()
    }

    fun getPackageById(id: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_package WHERE Package_ID = ? ORDER BY Package_ID"
        )
        statement.setInt(1, id)
        return statement.executeQuery()
    }

    fun getPackageByParentId(id: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_package WHERE Parent_ID = ? ORDER BY Package_ID"
        )
        statement.setInt(1, id)
        return statement.executeQuery()
    }

    fun getClassesByParentPackageId(id: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_object WHERE Package_ID = ? AND Object_Type = 'Class' ORDER BY Object_ID"
        )
        statement.setInt(1, id)
        return statement.executeQuery()
    }

    fun getAttributesByObjectId(id: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_attribute WHERE Object_ID = ?"
        )
        statement.setInt(1, id)
        return statement.executeQuery()
    }

    fun getConnectorsByObjectId(id: Int): ResultSet {
        val statement = connection.prepareStatement(
            "SELECT * FROM t_connector WHERE Start_Object_ID = ? OR End_Object_ID = ?"
        )
        statement.setInt(1, id)
        statement.setInt(2, id)
        return statement.executeQuery()
    }
}
