package com.zepben.profilemanager.models

import kotlinx.serialization.Serializable

@Serializable
sealed class Element {
    abstract val id: Int
    abstract val name: String
    abstract val children: List<Element>

    open fun addChild(element: Element) {}

    operator fun get(path: String): Element? {
        if (path.isBlank()) {
            return this
        }

        val parts = path.lowercase().split(".")
        val firstPart = parts.first()

        val elem = children.firstOrNull { it.name.lowercase() == firstPart }
        val newPath = parts.subList(1, parts.size).joinToString(".")
        return elem?.get(newPath)
    }
}
