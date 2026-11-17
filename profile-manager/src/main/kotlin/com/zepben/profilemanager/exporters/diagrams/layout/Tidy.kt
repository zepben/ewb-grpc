/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters.diagrams.layout


/**
 * Improve on ELK's routing and labelling: straighten associations where that's clearly better (see
 * [AssociationStraightener]), move labels that lines run through (see [LabelPlacer.improve]), make labels hug their
 * lines (see [LabelPlacer.alignToLines]), then grow the diagram to fit any labels moved off its edge.
 */
fun tidy(layout: DiagramLayout): DiagramLayout =
    LabelPlacer.alignToLines(LabelPlacer.improve(AssociationStraightener.straighten(layout))).withLabelsInBounds()
