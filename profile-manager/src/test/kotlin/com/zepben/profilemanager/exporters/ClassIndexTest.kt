/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters

import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Package
import com.zepben.profilemanager.models.Root
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ClassIndexTest {

    private val first = Class(name = "Profile", description = "first")
    private val last = Class(name = "Profile", description = "last")
    private val root = Root(0, "TC57CIM").apply {
        addChild(Package(name = "A").apply { addChild(first) })
        addChild(Package(name = "B").apply { addChild(Class(name = "Other")); addChild(last) })
    }

    @Test
    fun `classes link to their pages under the prefix`() {
        val index = ClassIndex.of(root, "cim100")

        assertEquals("/cim100/TC57CIM/B/Other", index.urlOf("Other"))
        assertEquals("/TC57CIM/B/Other", ClassIndex.of(root, "").urlOf("Other"))
    }

    @Test
    fun `the last of classes sharing a name wins, with a warning`() {
        val warnings = mutableListOf<String>()
        val index = ClassIndex.of(root, "", warn = { warnings += it })

        assertSame(last, index["Profile"]?.cls)
        assertEquals("/TC57CIM/B/Profile", index.urlOf("Profile"))
        assertEquals(1, warnings.size, warnings.toString())
    }

    @Test
    fun `a shared name finds the class in the nearest package`() {
        val index = ClassIndex.of(root, "", warn = {})

        assertSame(first, index.get("Profile", from = listOf("A", "Sub"))?.cls)
        assertSame(first, index.get("Profile", from = first)?.cls)
        // Equally near both, so the same as without a package.
        assertSame(last, index.get("Profile", from = listOf("C"))?.cls)
    }

}
