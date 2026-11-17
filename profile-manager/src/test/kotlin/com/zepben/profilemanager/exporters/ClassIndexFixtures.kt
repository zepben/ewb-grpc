/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters

import com.zepben.profilemanager.models.Class

/** An index of [classes], each with a page at `/<name>`, for tests that don't care about packages. */
fun classIndexOf(vararg classes: Class): ClassIndex = ClassIndex(classes.associate { it.name to IndexedClass(it, "/${it.name}") })
