package com.zepben.profilemanager.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Class(
    @Transient override val id: Int = 0,
    override val name: String,
    val description: String? = null,
    private val attributes: MutableList<Attribute> = mutableListOf(),
    private val ancestors: MutableList<String> = mutableListOf(),
    private val descendants: MutableList<String> = mutableListOf(),
    private val associations: MutableList<Link> = mutableListOf(),
    var type: String? = attributes.classIsEnum(),
) : Element() {

    @Transient
    override val children: List<Element> = listOf()

    @Transient
    private val initialType = type

    val isEnum get() = (type == "enum")

    fun addAssociation(link: Link) {
        if (link.sourceCardinality == null && link.targetCardinality == null) {
            if (link.source == this.name) {
                ancestors.add(link.target)
            } else if (link.target == this.name) {
                descendants.add(link.source)
            }
        } else {
            val fixedLink = if (link.source != this.name) {
                link.swap()
            } else link

            associations.add(fixedLink)
        }
    }

    fun addAttribute(attribute: Attribute) {
        attributes.add(attribute)
        if (attribute.type == "enum") {
            if (type == null)
                type = "enum"
        } else if (type == "enum")
            type = initialType
    }

    fun associations() = associations.filter { it.sourceCardinality != null || it.targetCardinality != null }.sortedBy { it.source }

    // the source / target doesn't really make sense here
    // one must remember that the arrow always points to the ancestor class, not the other way
    fun ancestors() = ancestors.sorted()
    fun descendants() = descendants.sorted()

    fun attributes(): List<Attribute> = attributes.sortedBy { it.name }

    fun sorted(): Class = copy(
        attributes = attributes.sortedWith(attributeOrder).toMutableList(),
        ancestors = ancestors.sorted().toMutableList(),
        descendants = descendants.sorted().toMutableList(),
        associations = associations.sortedWith(associationOrder).toMutableList(),
    )

    companion object {

        private val attributeOrder = compareBy<Attribute>(
            { it.name.lowercase() },
            { it.name },
            { it.type },
            { it.description },
            { it.nullable },
        )

        private val associationOrder = compareBy<Link>(
            { (it.targetName ?: it.target).lowercase() },
            { it.targetName ?: it.target },
            { it.target },
            { it.sourceName },
            { it.targetCardinality },
            { it.sourceCardinality },
            { it.targetDescription },
            { it.sourceDescription },
        )

        private fun List<Attribute>.classIsEnum(): String? =
            if ((any() && all { (it.type == "enum") }) || (any() && all { (it.type == null) })) "enum" else null

    }

}
