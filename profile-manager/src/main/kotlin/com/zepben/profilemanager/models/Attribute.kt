package com.zepben.profilemanager.models

import kotlinx.serialization.Serializable

@Serializable
data class Attribute(val name: String, val type: String? = null, val description: String? = null)
