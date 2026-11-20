package com.zepben.profilemanager.models

import kotlinx.serialization.Serializable

@Serializable
// NOTE: All attributes, except enum values, should have a defined type. 
data class Attribute(val name: String, val type: String? = null, val description: String? = null, val nullable: Boolean? = null)
