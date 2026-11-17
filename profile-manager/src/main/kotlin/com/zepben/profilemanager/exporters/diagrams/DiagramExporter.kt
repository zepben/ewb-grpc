/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams

import com.zepben.profilemanager.exporters.ClassIndex
import com.zepben.profilemanager.exporters.diagrams.layout.BestLayout
import com.zepben.profilemanager.exporters.diagrams.layout.BoxSizer
import com.zepben.profilemanager.exporters.diagrams.layout.TextMeasurer
import com.zepben.profilemanager.exporters.diagrams.model.DiagramDefinition
import com.zepben.profilemanager.exporters.diagrams.model.DiagramFile
import com.zepben.profilemanager.exporters.diagrams.model.DiagramModelBuilder
import com.zepben.profilemanager.exporters.diagrams.render.SvgRenderer
import com.zepben.profilemanager.exporters.diagrams.render.diagramPageMdx
import com.zepben.profilemanager.exporters.printWarning
import com.zepben.profilemanager.exporters.sitePath
import java.io.File

/**
 * A UML diagram page, linked from the pages of the classes drawn in it.
 *
 * @property name The diagram's display name.
 * @property packagePath The package path the diagram is in (see [DiagramDefinition.packagePath]), shown to tell apart
 *   diagrams with the same name.
 * @property url The site relative URL of the diagram's page.
 */
data class DiagramPage(val name: String, val packagePath: String, val url: String)

/**
 * A drawn diagram, ready to write.
 *
 * @property classes The names of the classes drawn in it.
 * @property svg The rendered SVG document.
 */
internal data class GeneratedDiagram(val definition: DiagramDefinition, val classes: List<String>, val svg: String)

/**
 * Draws the diagrams defined in a profile's diagram definitions file ([DiagramFile.FILE_NAME] at the top of the
 * profile) as SVGs, and writes each out with a page showing it.
 *
 * Each diagram is written as `<profile output>/<profile>/<path>.svg`, with its page right next to it at `<path>.mdx`,
 * alongside the class pages.
 *
 * @param classes The profile's classes, with the URLs of their pages.
 * @param profile The profile name, the first folder of each diagram's path.
 * @param exportPath The profile's folder within the output, which is also the start of its page URLs.
 * @param links Whether to link each class box to its documentation page.
 * @param fontFamily The font to measure and render text with.
 */
class DiagramExporter(
    private val classes: ClassIndex,
    private val profile: String,
    private val exportPath: String,
    links: Boolean,
    private val fontFamily: String,
    private val warn: (String) -> Unit = ::printWarning,
) {

    private val builder = DiagramModelBuilder(classes)

    // Lazy, so profiles without diagrams don't measure fonts or warn about them.
    private val layout by lazy {
        val measurer = TextMeasurer(fontFamily)
        if (measurer.isFallback)
            warn("font '$fontFamily' is not installed, measuring text with '${measurer.family}' instead. Boxes may not fit their text.")
        BestLayout(BoxSizer(measurer), warn = warn)
    }

    private val renderer = SvgRenderer(fontFamily, links)

    /**
     * Draw every diagram in the profile's definitions file (the [DiagramFile.FILE_NAME] in its input folder
     * [profileInput]) and write each to the profile's output folder [outputPath]. A profile without a definitions file
     * has no diagrams.
     *
     * Diagrams that would replace a class page, or another diagram, are skipped.
     *
     * @return The pages of the diagrams each class is drawn in, by class name.
     */
    fun export(profileInput: File, outputPath: File): Map<String, List<DiagramPage>> {
        val definitionsFile = profileInput.resolve(DiagramFile.FILE_NAME)
        if (!definitionsFile.isFile)
            return emptyMap()

        val definitions = DiagramFile.load(definitionsFile).diagrams
        println("Generating ${definitions.size} $profile diagram(s)...")

        val seenUrls = mutableSetOf<String>()
        val diagrams = definitions
            .map { it to pageOf(it) }
            .filter { (definition, page) ->
                when {
                    page.url in classes.urls -> false.also { warn("diagram '${definition.path}' would replace the page of a class at ${page.url}, skipping it.") }
                    !seenUrls.add(page.url) -> false.also { warn("diagram '${definition.path}' is defined more than once, skipping all but the first.") }
                    else -> true
                }
            }
            .mapNotNull { (definition, page) -> generate(definition)?.let { it to page } }

        diagrams.forEach { (diagram, _) -> write(diagram, outputPath) }
        println("Generated ${diagrams.size} $profile diagram(s).")

        return diagrams
            .flatMap { (diagram, page) -> diagram.classes.map { it to page } }
            .groupBy({ it.first }, { it.second })
    }

    /**
     * Draw a single diagram, returning null if it has nothing to draw or can't be laid out.
     */
    internal fun generate(definition: DiagramDefinition): GeneratedDiagram? {
        val model = builder.build(definition)
        model.unknownClasses.forEach { warn("diagram '${definition.path}' references unknown class '$it', skipping it.") }

        if (model.nodes.isEmpty()) {
            warn("diagram '${definition.path}' has no classes to draw, skipping it.")
            return null
        }

        val laidOut = runCatching { layout.layout(model) }.getOrElse {
            warn("diagram '${definition.path}' could not be laid out (${it.message}), skipping it.")
            return null
        }

        return GeneratedDiagram(definition, model.nodes.map { it.name }, renderer.render(laidOut, definition.displayName))
    }

    /** Write [diagram] as `<outputPath>/<relativePath>.svg`, with its page next to it at `<relativePath>.mdx`. */
    private fun write(diagram: GeneratedDiagram, outputPath: File) {
        val base = outputPath.resolve(relativePath(diagram.definition))
        val svgFile = base.resolveSibling("${base.name}.svg")
        svgFile.parentFile.mkdirs()
        svgFile.writeText(diagram.svg)

        base.resolveSibling("${base.name}.mdx")
            .writeText(diagramPageMdx(diagram.definition.displayName, slug = base.name, svgPath = "./${svgFile.name}"))
    }

    /** Where [definition]'s diagram lives relative to the profile's output folder, without an extension. */
    private fun relativePath(definition: DiagramDefinition) = "$profile/${definition.path.trim('/')}"

    private fun pageOf(definition: DiagramDefinition) =
        DiagramPage(definition.displayName, definition.packagePath, sitePath(exportPath, relativePath(definition)))

}
