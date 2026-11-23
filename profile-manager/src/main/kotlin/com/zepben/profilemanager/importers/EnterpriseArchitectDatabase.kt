package com.zepben.profilemanager.importers

import com.zepben.profilemanager.db.EaAccessDatabase
import com.zepben.profilemanager.exporters.diagrams.model.DiagramClass
import com.zepben.profilemanager.exporters.diagrams.model.DiagramContent
import com.zepben.profilemanager.exporters.diagrams.model.DiagramDefinition
import com.zepben.profilemanager.exporters.diagrams.model.DiagramFile
import com.zepben.profilemanager.exporters.diagrams.model.EdgeKind
import com.zepben.profilemanager.exporters.printWarning
import com.zepben.profilemanager.models.*
import java.io.File
import java.sql.ResultSet
import kotlin.collections.ArrayDeque

class EnterpriseArchitectDatabase(inputSource: File, private val warn: (String) -> Unit = ::printWarning) : Importer(inputSource) {

    private lateinit var db: EaAccessDatabase

    override fun import(profile: String): Root {
        db = EaAccessDatabase.connect(inputSource)

        val root = importPackages(profile)
        importClasses(root)
        root.reindex()
        importAttributesAndAssociations(root)

        return root
    }

    /**
     * Read the class diagrams of the profile [root] previously returned by [import]. Each diagram includes the
     * profile's classes drawn on it, and roughly shows the connectors between them that EA draws. Diagrams without any
     * of the profile's classes are skipped.
     */
    fun importDiagrams(root: Root): DiagramFile {
        val classCounts = mutableMapOf<String, Int>()
        root.iterate(classCallback = { classCounts.merge(it.name, 1, Int::plus) })
        val ambiguous = mutableSetOf<String>()

        val diagrams = mutableListOf<DiagramDefinition>()
        fun visit(element: Element, packagePath: String) {
            val taken = element.children.filterIsInstance<Class>().mapTo(mutableSetOf()) { it.name }
            val rows = db.getDiagramsByPackageId(element.id)
            while (rows.next()) {
                val name = rows.getString("Name")?.trim().orEmpty()
                val slug = uniqueSlug(name, taken).also { taken.add(it) }
                val path = listOf(packagePath, slug).filter { it.isNotEmpty() }.joinToString("/")
                importDiagram(rows.getInt("Diagram_ID"), path, name.takeIf { it != slug }, root)?.let { diagram ->
                    diagrams.add(diagram)
                    diagram.classes.map { it.name }.filterTo(ambiguous) { (classCounts[it] ?: 0) > 1 }
                }
            }

            element.children.filterIsInstance<Package>().forEach {
                visit(it, listOf(packagePath, it.name).filter { p -> p.isNotEmpty() }.joinToString("/"))
            }
        }
        visit(root, "")

        ambiguous.sorted().forEach {
            warn("class name '$it' is used by ${classCounts[it]} classes in ${root.name}, diagrams including it will show only one of them.")
        }

        return DiagramFile(diagrams.sortedBy { it.path })
    }

    private fun importDiagram(id: Int, path: String, name: String?, root: Root): DiagramDefinition? {
        val objects = db.getDiagramObjectIds(id)
        val classes = buildSet {
            while (objects.next())
                root.getClass(objects.getInt("Object_ID"))?.let { add(it.name) }
        }.sorted()

        if (classes.isEmpty())
            return null

        // A connector may be linked to the diagram more than once, it's hidden if any of them are.
        val connectors = mutableMapOf<Int, DiagramConnector>()
        val rows = db.getDiagramConnectors(id)
        while (rows.next()) {
            val start = root.getClass(rows.getInt("Start_Object_ID")) ?: continue
            val end = root.getClass(rows.getInt("End_Object_ID")) ?: continue
            if (start.name == end.name)
                continue
            // Classified the same way as when building the spec, see Class.addAssociation.
            val kind = if (rows.getString("SourceCard") == null && rows.getString("DestCard") == null)
                EdgeKind.GENERALISATION
            else
                EdgeKind.ASSOCIATION
            val connector = DiagramConnector(setOf(start.name, end.name), kind, visible = !rows.getBoolean("Hidden"))
            connectors.merge(rows.getInt("Connector_ID"), connector) { a, b -> a.copy(visible = a.visible && b.visible) }
        }

        // Draw each kind of connector EA mostly draws, then override that for any class whose connectors of a kind
        // all disagree. This won't always match EA exactly, but is close enough to tidy up by hand.
        fun Collection<DiagramConnector>.mostlyDrawn(kind: EdgeKind) =
            filter { it.kind == kind }.let { of -> of.count { it.visible } >= of.count { !it.visible } }
        val show = contentOf(connectors.values.mostlyDrawn(EdgeKind.GENERALISATION), connectors.values.mostlyDrawn(EdgeKind.ASSOCIATION))
            ?: DiagramContent.BOTH

        fun Collection<DiagramConnector>.drawn(kind: EdgeKind) = filter { it.kind == kind }.let { of ->
            val default = show.draws(kind)
            if (of.isNotEmpty() && of.all { it.visible != default }) !default else default
        }
        return DiagramDefinition(path, name, show, classes.map { cls ->
            val own = connectors.values.filter { cls in it.ends }
            DiagramClass(cls, contentOf(own.drawn(EdgeKind.GENERALISATION), own.drawn(EdgeKind.ASSOCIATION))?.takeIf { it != show })
        })
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
                sourceRole?.toRoleName(),
                sourceDescription,
                endCardinality,
                endRole?.toRoleName(),
                endDescription
            )

            item.addAssociation(assoc)
        }
    }

    private fun importAttributes(item: Class) {
        val attrs = db.getAttributesByObjectId(item.id)

        while (attrs.next()) {
            val name = attrs.getString("Name")
            val type = attrs.getString("Type") ?: "enum".also {
                if (attrs.getString("Stereotype") != "enum")
                    warn("attribute '${item.name}.$name' has no type, treating it as an enum literal.")
            }
            val notes = attrs.getString("Notes")?.fixWindowsNewLines()

            val attr = Attribute(name, type, description = notes)
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
                    id = classes.getInt("Object_ID"),
                    name = classes.getString("Name").trim(),
                    description = classes.getString("Note")?.fixWindowsNewLines()
                )
                item.addChild(c)
            }

            queue.addAll(item.children)
        } while (!queue.isEmpty())
    }

    private fun importPackages(profileName: String): Root {
        val queue = ArrayDeque<Pair<Element, Int>>()
        val rootProfile = db.getPackageByName(profileName)
        require(rootProfile.next()) {"Unable to find profile '$profileName', are you sure you set it correctly?"}
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

/** A connector between two classes on an EA diagram, and whether the diagram draws it. */
private data class DiagramConnector(val ends: Set<String>, val kind: EdgeKind, val visible: Boolean)

/** The diagram content that draws the given kinds of connector, or null if neither. */
private fun contentOf(inheritance: Boolean, relationships: Boolean): DiagramContent? = when {
    inheritance && relationships -> DiagramContent.BOTH
    inheritance -> DiagramContent.INHERITANCE
    relationships -> DiagramContent.RELATIONSHIPS
    else -> null
}

/**
 * A diagram name usable as a file name and URL segment: [name] with only its letters, digits, underscores and dashes,
 * with a numeric suffix if that's already in [taken].
 */
internal fun uniqueSlug(name: String, taken: Set<String>): String {
    val base = name.replace(Regex("[^A-Za-z0-9_-]"), "").ifEmpty { "Diagram" }
    if (base !in taken)
        return base
    return generateSequence(2) { it + 1 }.map { "${base}_$it" }.first { it !in taken }
}

private fun ResultSet.read(): ResultSet {
    next()
    return this
}

/**
 * Use `\n` line breaks, and drop whitespace from the start and end of the text and trailing whitespace from each line.
 * EA notes often have stray whitespace, which would stop the text being written as a YAML block scalar or wrapped.
 */
private fun String.fixWindowsNewLines(): String {
    return trim().replace("\r\n", "\n").replace(Regex("""[ \t]+\n"""), "\n")
}

/**
 * Role names in the EA model with irregular casing that the generic acronym rule in [toRoleName] can't resolve.
 */
private val roleNameOverrides = mapOf(
    "DCvoltageControl" to "dcVoltageControl",
    "HVDClookUpTable" to "hvdcLookUpTable",
    "VSCtype1" to "vscType1",
    "PFmode" to "pfMode",
    "PFmodel" to "pfModel",
    "WIndContQIEC" to "windContQIEC",
    "PFVArControllerType1Dynamics" to "pfVArControllerType1Dynamics",
    "PFVArControllerType1UserDefined" to "pfVArControllerType1UserDefined",
    "PFVArControllerType2Dynamics" to "pfVArControllerType2Dynamics",
    "PFVArControllerType2UserDefined" to "pfVArControllerType2UserDefined",
)

private val pluralAcronymSuffix = Regex("^s($|[A-Z_0-9])")

/**
 * Convert a PascalCase EA role name to camelCase, treating a leading acronym as a single word,
 * e.g. `Terminals` -> `terminals`, `ACLineSegment` -> `acLineSegment`, `RTOs` -> `rtos`, `TASE2Table` -> `tase2Table`.
 */
private fun String.toRoleName(): String {
    roleNameOverrides[this]?.let { return it }

    val runLength = takeWhile { it.isUpperCase() }.length
    if (runLength <= 1)
        return replaceFirstChar { it.lowercase() }

    val run = substring(0, runLength)
    val rest = substring(runLength)
    return when {
        rest.isEmpty() || pluralAcronymSuffix.containsMatchIn(rest) -> run.lowercase() + rest
        rest[0].isLowerCase() -> run.dropLast(1).lowercase() + run.last() + rest
        else -> run.lowercase() + rest
    }
}
