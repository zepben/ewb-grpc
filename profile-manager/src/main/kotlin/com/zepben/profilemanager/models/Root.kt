package com.zepben.profilemanager.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Root(@Transient override val id: Int = 0, override val name: String) : Element() {

    @Transient
    override val children: MutableList<Element> = mutableListOf()

    private val index: MutableMap<Int, Class> = mutableMapOf()

    override fun addChild(element: Element) {
        children.add(element)
    }

    fun iterate(rootCallback: (Root) -> Unit = { _ -> } , packageCallback: (Package) -> Unit = { _ -> } , classCallback: (Class) -> Unit = { _ -> } , node: Element = this) {
        when (node) {
            is Class -> classCallback(node)
            is Package -> {
                packageCallback(node)
                node.children.forEach { iterate(rootCallback, packageCallback, classCallback, it)}
            }
            is Root -> {
                rootCallback(node)
                node.children.forEach { iterate(rootCallback, packageCallback, classCallback, it)}
            }
        }
    }

    fun <S> iterateWithState(rootCallback: (Root, S) -> S = { _, i -> i } , packageCallback: (Package, S) -> S = { _, i -> i } , classCallback: (Class, S) -> Unit = { _, _ -> } , node: Element = this, initialState: S) {
        when (node) {
            is Class -> classCallback(node, initialState)
            is Package -> {
                val newState = packageCallback(node, initialState)
                node.children.forEach { iterateWithState(rootCallback, packageCallback, classCallback, it, newState)}
            }
            is Root -> {
                val newState = rootCallback(node, initialState)
                node.children.forEach { iterateWithState(rootCallback, packageCallback, classCallback, it, newState)}
            }
        }
    }

    fun reindex() {
        index.clear()

        val queue = ArrayDeque<Element>()
        queue.add(this)

        while (!queue.isEmpty()) {
            val item = queue.removeFirst()
            queue.addAll(item.children)

            item.children.filterIsInstance<Class>().forEach {
                index[it.id] = it
            }
        }
    }

    fun getClass(id: Int): Class? {
        return index[id]
    }
}
