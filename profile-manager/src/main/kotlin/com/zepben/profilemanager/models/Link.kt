package com.zepben.profilemanager.models

import kotlinx.serialization.Serializable

@Serializable
data class Link(
    val source: String,
    val target: String,
    val sourceCardinality: String? = null,
    val sourceName: String? = null,
    val sourceDescription: String? = null,
    val targetCardinality: String? = null,
    val targetName: String? = null,
    val targetDescription: String? = null
) {
    fun swap(): Link {
        return Link(
            this.target,
            this.source,
            this.targetCardinality,
            this.targetName,
            this.targetDescription,
            this.sourceCardinality,
            this.sourceName,
            this.sourceDescription
        )
    }
}
