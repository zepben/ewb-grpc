/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout

import com.zepben.profilemanager.exporters.diagrams.model.DiagramNode
import com.zepben.profilemanager.exporters.diagrams.*
import kotlin.math.ceil
import kotlin.math.max

/**
 * Sizes class boxes and labels to fit their text.
 */
class BoxSizer(private val measurer: TextMeasurer) {

    fun size(node: DiagramNode): SizedNode {
        val headerWidths = node.stereotypes.map { measurer.width("«$it»", STEREOTYPE_FONT_SIZE, italic = true) } +
            listOfNotNull(node.parentLabel?.let { measurer.width(it, STEREOTYPE_FONT_SIZE, italic = true) + PARENT_PADDING_X - PADDING_X }) +
            measurer.width(node.name, NAME_FONT_SIZE, bold = true)

        val rowWidths = node.rows.map { row ->
            measurer.width(row.text, ATTRIBUTE_FONT_SIZE) +
                if (row.isZbex) MARKER_GAP + measurer.width(ZBEX_ROW_MARKER, STEREOTYPE_FONT_SIZE, italic = true) else 0.0
        }

        val contentWidth = (headerWidths + rowWidths).maxOrNull() ?: 0.0
        val width = max(MIN_WIDTH, ceil(contentWidth * TEXT_SLACK + 2 * PADDING_X))

        // The parent line, stereotype lines and name at the top of the box.
        val titleHeight = (if (node.parentLabel == null) 0.0 else STEREOTYPE_LINE_HEIGHT) +
            node.stereotypes.size * STEREOTYPE_LINE_HEIGHT +
            NAME_LINE_HEIGHT

        // Classes with no attributes of their own are just a box around the title, with no empty compartment below.
        if (node.rows.isEmpty()) {
            val height = titleHeight + 2 * TITLE_ONLY_PADDING_Y
            return SizedNode(node, width, height, headerHeight = height, titleHeight)
        }

        val headerHeight = titleHeight + 2 * HEADER_PADDING_Y
        return SizedNode(node, width, headerHeight + 2 * BODY_PADDING_Y + node.rows.size * ROW_HEIGHT, headerHeight, titleHeight)
    }

    fun labelWidth(text: String): Double = ceil(measurer.width(text, LABEL_FONT_SIZE) * TEXT_SLACK)

}
