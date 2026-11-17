/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.model

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File

/**
 * The contents of a profile's diagram definitions file, e.g. `spec/ewb/__diagrams.yml`.
 */
@Serializable
data class DiagramFile(val diagrams: List<DiagramDefinition>) {

    companion object {

        /** The name of the diagram definitions file at the top of each profile. */
        const val FILE_NAME = "__diagrams.yml"

        fun load(file: File): DiagramFile = parse(file.readText())

        fun parse(yaml: String): DiagramFile = Yaml.default.decodeFromString(serializer(), yaml)

    }

}

/**
 * A single diagram.
 *
 * @property path Where the diagram lives within the profile, without an extension, e.g. `IEC61970/Base/Core/Main`.
 * @property name The display name of the diagram. Defaults to the last segment of [path].
 * @property classes The classes to include in the diagram.
 * @property show Which relationships between the classes to draw, unless overridden by a class (see [DiagramClass.show]).
 */
@Serializable
data class DiagramDefinition(
    val path: String,
    val name: String? = null,
    val show: DiagramContent = DiagramContent.BOTH,
    val classes: List<DiagramClass>,
) {

    val displayName: String get() = name ?: path.trimEnd('/').substringAfterLast('/')

    /** The package path the diagram is in, e.g. `IEC61970/Base/Core`. Blank for a diagram at the top of the profile. */
    val packagePath: String get() = path.trim('/').substringBeforeLast('/', "")

    /**
     * Whether a connector of [kind] between classes [a] and [b] should be drawn. Each end's own `show` overrides
     * the diagram's for that end's connectors, so one override is enough to show or hide a connector. If both ends have
     * overrides that disagree, the more restrictive one wins.
     */
    fun shows(a: String, b: String, kind: EdgeKind): Boolean {
        val overrides = classes.filter { it.name == a || it.name == b }.mapNotNull { it.show }
        return if (overrides.isEmpty()) show.draws(kind) else overrides.all { it.draws(kind) }
    }

}

/**
 * A class in a diagram. In YAML this is either just the class name, or a map with the name and options:
 * ```
 * classes:
 *   - Meter
 *   - name: UsagePoint
 *     show: both
 * ```
 *
 * @property show Overrides the diagram's `show` for the connectors to and from this class.
 */
@Serializable(with = DiagramClassSerializer::class)
data class DiagramClass(val name: String, val show: DiagramContent? = null)

/**
 * Reads a [DiagramClass] from either a plain scalar (the class name) or a map.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
object DiagramClassSerializer : KSerializer<DiagramClass> {

    @Serializable
    private data class Options(val name: String, val show: DiagramContent? = null)

    // Contextual, so kaml hands over the raw node whether it's a scalar or a map.
    override val descriptor: SerialDescriptor = buildSerialDescriptor("DiagramClass", SerialKind.CONTEXTUAL)

    override fun deserialize(decoder: Decoder): DiagramClass {
        // Beginning the structure gives the input for this element's own node (as documented by kaml).
        val input = decoder.beginStructure(descriptor) as? YamlInput
            ?: throw SerializationException("Diagram classes can only be read from YAML")
        val result = when (val node = input.node) {
            is YamlScalar -> DiagramClass(node.content)
            is YamlMap -> input.yaml.decodeFromYamlNode(Options.serializer(), node).let { DiagramClass(it.name, it.show) }
            else -> throw SerializationException("Expected a class name or a map at ${node.path.toHumanReadableString()}")
        }
        input.endStructure(descriptor)
        return result
    }

    override fun serialize(encoder: Encoder, value: DiagramClass) {
        if (value.show == null)
            encoder.encodeString(value.name)
        else
            encoder.encodeSerializableValue(Options.serializer(), Options(value.name, value.show))
    }

}

/**
 * Which kinds of connector a diagram draws between its classes.
 */
@Serializable
enum class DiagramContent(private val inheritance: Boolean, private val relationships: Boolean) {

    /** Only generalisations, e.g. for an inheritance hierarchy. */
    @SerialName("inheritance")
    INHERITANCE(inheritance = true, relationships = false),

    /** Only associations. */
    @SerialName("relationships")
    RELATIONSHIPS(inheritance = false, relationships = true),

    /** Both generalisations and associations. */
    @SerialName("both")
    BOTH(inheritance = true, relationships = true);

    fun draws(kind: EdgeKind): Boolean = when (kind) {
        EdgeKind.GENERALISATION -> inheritance
        EdgeKind.ASSOCIATION -> relationships
    }

}
