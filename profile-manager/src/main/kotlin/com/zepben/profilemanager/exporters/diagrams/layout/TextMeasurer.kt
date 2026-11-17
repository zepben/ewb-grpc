/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.font.FontRenderContext

/**
 * Measures text using AWT font metrics so boxes can be sized to fit their contents. At a font size of N, widths are
 * returned in the same units as an SVG `font-size` of N, so the measurements can be used directly as SVG coordinates.
 *
 * @param requestedFamily The font family to measure with (and that the SVG asks for). Falls back to the JVM's logical
 *   sans-serif font if it isn't installed.
 */
class TextMeasurer(requestedFamily: String) {

    val family: String
    val isFallback: Boolean

    init {
        // Only fonts are needed, so AWT mustn't try to open a display. This has to be set before AWT is first used.
        System.setProperty("java.awt.headless", "true")
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        isFallback = requestedFamily !in available
        family = if (isFallback) Font.SANS_SERIF else requestedFamily
    }

    private val frc = FontRenderContext(null, true, true)
    private val fonts = mutableMapOf<Pair<Int, Float>, Font>()

    fun width(text: String, size: Float, bold: Boolean = false, italic: Boolean = false): Double {
        if (text.isEmpty())
            return 0.0

        val style = (if (bold) Font.BOLD else 0) or (if (italic) Font.ITALIC else 0)
        val font = fonts.getOrPut(style to size) { Font(family, style, 1).deriveFont(size) }
        return font.getStringBounds(text, frc).width
    }

}
