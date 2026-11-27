/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.fixers

import com.zepben.profilemanager.models.Attribute
import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Link
import com.zepben.profilemanager.models.Package

object TrailingDotRemover : Fixer {

    override fun fix(original: Class): Class =
        original.copy(
            description = fix(original.description),
            attributes = original.attributes().map { fix(it) }.toMutableList(),
            associations = original.associations().map { fix(it) }.toMutableList(),
        )

    override fun fix(original: Package): Package =
        original.copy(description = fix(original.description))

    private fun fix(original: Attribute): Attribute =
        original.copy(description = fix(original.description))

    private fun fix(original: Link): Link =
        original.copy(
            sourceDescription = fix(original.sourceDescription),
            targetDescription = fix(original.targetDescription),
        )

    private fun fix(original: String?): String? =
        original?.removeSuffix("\n.")?.trim()

}
