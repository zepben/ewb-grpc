/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * How diagrams look: the sizes and spacing shared by box sizing, layout and rendering, and the colour palette. Tuning
 * parameters of the layout algorithms live with the algorithms.
 */
package com.zepben.profilemanager.exporters.diagrams

import com.zepben.profilemanager.exporters.ZBEX_STEREOTYPE

// Text.
const val NAME_FONT_SIZE = 12f
const val STEREOTYPE_FONT_SIZE = 10f
const val ATTRIBUTE_FONT_SIZE = 11f
const val LABEL_FONT_SIZE = 10f

const val NAME_LINE_HEIGHT = 16.0
const val STEREOTYPE_LINE_HEIGHT = 13.0
const val ROW_HEIGHT = 16.0
const val LABEL_HEIGHT = 13.0

/** Browsers may not have the exact font we measured with, so leave a little slack. */
const val TEXT_SLACK = 1.06

// Class boxes.
const val MIN_WIDTH = 100.0
const val PADDING_X = 10.0
const val HEADER_PADDING_Y = 7.0
const val BODY_PADDING_Y = 5.0

/** Radius of the rounded corners of class boxes. */
const val BOX_CORNER_RADIUS = 3

/** Vertical padding around the title of a class with no attributes, which is drawn without an attribute compartment. */
const val TITLE_ONLY_PADDING_Y = 11.0

/** Space between the right edge of a box and the name of an absent superclass in its top right corner. */
const val PARENT_PADDING_X = 6.0

/** The marker drawn right aligned on the row of an attribute that is a Zepben extension. */
const val ZBEX_ROW_MARKER = "«$ZBEX_STEREOTYPE»"

/** Gap between attribute text and the [ZBEX_ROW_MARKER]. */
const val MARKER_GAP = 12.0

// Connectors.
/** Length (and width) of the hollow triangle at the superclass end of a generalisation. */
const val GENERALISATION_ARROW = 14.0

/** The least amount of line visible below a generalisation triangle before the line turns. */
const val MIN_ARROW_STEM = 12.0

/** Space to the left of stacked leaf subclasses for the bus line joining them to their superclass. */
const val BUS_INDENT = 24.0

/** Vertical space between stacked leaf subclasses. */
const val STACK_GAP = 12.0

// The diagram.
/** Space between the diagram's contents and its edge. */
const val DIAGRAM_PADDING = 20.0

/** The least space kept between an edge label and the edge of the diagram. */
const val LABEL_EDGE_MARGIN = 4.0

// Colours.
/**
 * A colour of the diagram: the page's [themeVariable] when there is one, else [fallback], which is the data model site's
 * (evolve-datamodel-site) light theme value for that variable.
 */
internal data class ThemeColour(val themeVariable: String, val fallback: String)

/** The diagram's colours, each exposed to the SVG's styles as `--uml-<name>`. */
internal val PALETTE = listOf(
    "text" to ThemeColour("--ifm-font-color-base", "#1c1e21"),
    "label" to ThemeColour("--ifm-color-emphasis-800", "#444950"),
    "line" to ThemeColour("--ifm-color-emphasis-700", "#606770"),
    "border" to ThemeColour("--ifm-color-emphasis-600", "#8d949e"),
    "box" to ThemeColour("--ifm-background-surface-color", "#ffffff"),
    "header" to ThemeColour("--ifm-table-head-background", "#a3e6da"),
    "zbex-header" to ThemeColour("--ifm-color-primary-contrast-foreground", "#3c5573"),
    "zbex-header-text" to ThemeColour("--ifm-color-primary-contrast-background", "#c9f2e7"),
    "zbex-row" to ThemeColour("--zbex-row-background", "#e4f9f3"),
    "zbex-marker" to ThemeColour("--ifm-color-primary", "#3c5573"),
    "highlight" to ThemeColour("--ifm-color-primary", "#3c5573"),
)
