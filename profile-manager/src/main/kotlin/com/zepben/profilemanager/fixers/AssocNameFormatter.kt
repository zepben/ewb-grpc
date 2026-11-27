/*
 * Copyright 2026 Zeppelin Bend Pty Ltd
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.zepben.profilemanager.fixers

import com.zepben.profilemanager.models.Class
import com.zepben.profilemanager.models.Link
import kotlin.text.replaceFirstChar
import kotlin.text.substring
import kotlin.text.takeWhile

/**
 * There is a known example of an incorrectly formatted association name. This is limited to formatting issues,
 * not spelling, as spelling mistakes are part of the CIM contract.
 */
object AssocNameFormatter : Fixer {

    override fun fix(original: Class): Class =
        original.copy(
            associations = original.associations().map { fix(it) }.toMutableList(),
        )

    /**
     * Convert a PascalCase EA role name to camelCase, treating a leading acronym as a single word,
     * e.g. `Terminals` -> `terminals`, `ACLineSegment` -> `acLineSegment`, `RTOs` -> `rtos`, `TASE2Table` -> `tase2Table`.
     */
    private fun fix(original: Link): Link =
        original.copy(
            sourceName = fix(original.sourceName),
            targetName = fix(original.targetName),
        )

    private fun fix(original: String?): String? =
        original?.replace(" ", "")?.correctedRoleName()

    private fun String.correctedRoleName(): String? {
        roleNameOverrides[this]?.let { return it }

        val runLength = this.takeWhile { it.isUpperCase() }.length
        if (runLength <= 1)
            return this.replaceFirstChar { it.lowercase() }

        val run = this.substring(0, runLength)
        val rest = this.substring(runLength)
        return when {
            rest.isEmpty() || doesntContinueWord.containsMatchIn(rest) -> run.lowercase() + rest
            rest[0].isLowerCase() -> run.dropLast(1).lowercase() + run.last() + rest
            else -> run.lowercase() + rest
        }
    }

    /**
     * Role names in the EA model with irregular casing that the generic acronym rule in [fix] can't resolve.
     */
    @Suppress("SpellCheckingInspection")
    private val roleNameOverrides = mapOf(
        "DCvoltageControl" to "dcVoltageControl",
        "HVDClookUpTable" to "hvdcLookUpTable",
        "VSCtype1" to "vscType1",
        "PFmode" to "pfMode",
        "PFmodel" to "pfModel",
        "WIndContQIEC" to "windContQIEC",
        "PFVArControllerType1Dynamics" to "pfVArControllerType1Dynamics",
        "PFVArControllerType1UserDefined" to "pfVArControllerType1UserDefined",
        "PFVArControllerType2Dynamics" to "pfVArControllerType2Dynamics",
        "PFVArControllerType2UserDefined" to "pfVArControllerType2UserDefined",
        "signaltoNoiseRatio" to "signalToNoiseRatio",
    )

    private val doesntContinueWord = Regex("^s($|[A-Z_0-9])|^($|[_0-9])")

}
