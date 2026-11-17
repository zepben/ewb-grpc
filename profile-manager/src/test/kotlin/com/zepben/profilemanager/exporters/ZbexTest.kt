/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.exporters

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZbexTest {

    @Test
    fun `detects every spelling of the marker`() {
        assertTrue("[ZBEX] Unescaped.".isZbex())
        assertTrue("""\[ZBEX\] Escaped.""".isZbex())
        assertTrue("/[ZBEX/] Odd escaping.".isZbex())
        assertTrue("Marker part way through [ZBEX] a description.".isZbex())
    }

    @Test
    fun `ignores descriptions without the marker`() {
        assertFalse(null.isZbex())
        assertFalse("".isZbex())
        assertFalse("An ordinary CIM class.".isZbex())
        assertFalse("ZBEX without brackets.".isZbex())
    }

}
