package com.zepben.profilemanager.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Package(
    override val name: String = "",
    val description: String? = null,
    @Transient override val id: Int = 0
) : Element() {
    @Transient
    override val children: MutableList<Element> = mutableListOf()

    override fun addChild(element: Element) {
        children.add(element)
    }
}
