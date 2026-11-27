/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.fixers

import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Package

/**
 * Fix something in the in memory model.
 */
sealed interface Fixer {

    fun fix(original: Class): Class = original
    fun fix(original: Package): Package = original

    companion object {

        fun fix(original: Class): Class =
            FIXERS.fold(original) { acc, fixer -> fixer.fix(acc) }

        fun fix(original: Package): Package =
            FIXERS.fold(original) { acc, fixer -> fixer.fix(acc) }

        val FIXERS: List<Fixer> = listOf(
            SpellChecker,
            AssocNameFormatter,
            TrailingDotRemover,
        )

    }

}
