package com.zepben.profilemanager.importers

import com.zepben.profilemanager.db.EaAccessDatabase
import com.zepben.profilemanager.models.*
import java.io.File
import java.sql.ResultSet
import kotlin.collections.ArrayDeque

class EnterpriseArchitectDatabase(inputSource: File) : Importer(inputSource) {

    private lateinit var db: EaAccessDatabase

    override fun import(profile: String): Element {
        db = EaAccessDatabase.connect(inputSource)

        val root = importPackages(profile)
        importClasses(root)
        root.reindex()
        importAttributesAndAssociations(root)

        return root
    }

    private fun importAttributesAndAssociations(root: Root) {
        val queue = ArrayDeque<Element>()
        queue.add(root)

        do {
            when(val item = queue.removeFirst()) {
                is Root, is Package -> queue.addAll(item.children)
                is Class -> {
                    importAttributes(item)
                    importLinks(item, root)
                }
            }
        } while (!queue.isEmpty())
    }

    private fun importLinks(item: Class, root: Root) {
        val assocs = db.getConnectorsByObjectId(item.id)

        while (assocs.next()) {
            val startId = assocs.getInt("Start_Object_ID")
            val endId = assocs.getInt("End_Object_ID")
            val sourceCardinality = assocs.getString("SourceCard")
            val endCardinality = assocs.getString("DestCard")
            val sourceRole = assocs.getString("SourceRole")
            val endRole = assocs.getString("DestRole")
            val sourceDescription = assocs.getString("SourceRoleNote")?.fixWindowsNewLines()
            val endDescription = assocs.getString("DestRoleNote")?.fixWindowsNewLines()

            val startNode = root.getClass(startId)
            val endNode = root.getClass(endId)

            if (startNode == null || endNode == null) {
                continue
            }

            val assoc = Link(
                startNode.name,
                endNode.name,
                sourceCardinality,
                sourceRole,
                sourceDescription,
                endCardinality,
                endRole,
                endDescription
            )

            item.addAssociation(assoc)
        }
    }

    private fun importAttributes(item: Class) {
        val attrs = db.getAttributesByObjectId(item.id)

        while (attrs.next()) {
            val name = attrs.getString("Name")
            val type = attrs.getString("Type")
            val notes = attrs.getString("Notes")?.fixWindowsNewLines()

            val attr = Attribute(name, type, notes)
            item.addAttribute(attr)
        }
    }

    private fun importClasses(root: Root) {
        val queue = ArrayDeque<Element>()
        queue.add(root)

        do {
            val item = queue.removeFirst()
            val classes = db.getClassesByParentPackageId(item.id)

            while (classes.next()) {
                val c = Class(
                    classes.getInt("Object_ID"),
                    classes.getString("Name"),
                    classes.getString("Note")?.fixWindowsNewLines()
                )
                item.addChild(c)
            }

            queue.addAll(item.children)
        } while (!queue.isEmpty())
    }

    private fun importPackages(profileName: String): Root {
        val queue = ArrayDeque<Pair<Element, Int>>()
        val rootProfile = db.getPackageByName(profileName).read()
        val id = rootProfile.getInt("Package_ID")

        val root = Root(id, profileName)
        addChildren(root, queue)

        do {
            val item = queue.removeFirst()
            val parent = item.first
            val childId = item.second

            val obj = db.getPackageById(childId).read()
            val element: Element = Package(
                obj.getString("Name"),
                obj.getString("Notes")?.fixWindowsNewLines(),
                childId
            )

            parent.addChild(element)
            addChildren(element, queue)
        } while (!queue.isEmpty())
        return root
    }

    private fun addChildren(
        node: Element,
        queue: ArrayDeque<Pair<Element, Int>>
    ) {
        val children = db.getPackageByParentId(node.id)

        while (children.next()) {
            val id = children.getInt("Package_ID")
            queue.add(Pair(node, id))
        }
    }
}

private fun ResultSet.read(): ResultSet {
    next()
    return this
}

private fun String.fixWindowsNewLines(): String {
    return trimEnd().replace("\r\n", "\n")
}
