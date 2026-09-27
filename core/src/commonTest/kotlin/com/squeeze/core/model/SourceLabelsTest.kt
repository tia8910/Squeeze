package com.squeeze.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceLabelsTest {

    @Test
    fun `no photo source is ever labelled tape`() {
        MeasurementSource.entries.filter { SourceLabels.isPhoto(it.name) }.forEach {
            assertFalse(SourceLabels.short(it.name).contains("Tape"), it.name)
            assertFalse(SourceLabels.long(it.name).contains("Tape"), it.name)
        }
    }

    @Test
    fun `the upper-body photo scan from 27 Sept reads as a photo`() {
        assertTrue(SourceLabels.short("PHOTO_TRUNK_SCALED").startsWith("Photo"))
    }

    @Test
    fun `an unrecognised stored value is unknown, not tape`() {
        assertEquals("Unknown", SourceLabels.short("SOMETHING_NEW"))
    }
}
