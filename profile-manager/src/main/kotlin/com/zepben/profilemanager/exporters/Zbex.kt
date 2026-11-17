/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters

/**
 * The spec marks Zepben extensions in their descriptions using `[ZBEX]`, which appears in several escaped forms:
 * `[ZBEX]`, `\[ZBEX\]` and `/[ZBEX/]`.
 */
internal val ZBEX_MARKER = Regex("""[\\/]?\[ZBEX[\\/]?]""")

fun String?.isZbex(): Boolean = this != null && ZBEX_MARKER.containsMatchIn(this)

/** The stereotype drawn on diagram class boxes that are Zepben extensions, and on the rows of extension attributes. */
const val ZBEX_STEREOTYPE = "ZBEX"

/** The markup a [ZBEX_MARKER] in a description is rendered as on the class pages. */
internal const val ZBEX_PILL = "<span className='zbex-pill'>ZBEX</span>"

/** The class given to class page table rows describing a Zepben extension, so the whole row can be styled. */
internal const val ZBEX_ROW_CLASS = "zbex-row"
