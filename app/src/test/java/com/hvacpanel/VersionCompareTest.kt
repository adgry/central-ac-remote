package com.hvacpanel

import com.hvacpanel.update.VersionCompare.isNewer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The updater must never walk backwards, whatever a tag is named. */
class VersionCompareTest {

    @Test
    fun `a later tag is newer`() {
        assertTrue(isNewer("v1.0.1", "1.0.0"))
        assertTrue(isNewer("1.1.0", "1.0.9"))
        assertTrue(isNewer("v2.0.0", "1.9.9"))
    }

    @Test
    fun `segments compare as numbers, not text`() {
        assertTrue(isNewer("v1.10.0", "1.9.0"))
        assertFalse(isNewer("v1.9.0", "1.10.0"))
    }

    @Test
    fun `the same version is not an update`() {
        assertFalse(isNewer("v1.0.0", "1.0.0"))
        assertFalse(isNewer("1.0.0", "1.0.0"))
        assertFalse(isNewer("v1.0", "1.0.0"))
    }

    @Test
    fun `an older tag never downgrades the phone`() {
        assertFalse(isNewer("v0.9.0", "1.0.0"))
        assertFalse(isNewer("v1.0.0", "1.0.1"))
    }

    @Test
    fun `a tag we cannot read is not treated as newer`() {
        assertFalse(isNewer("", "1.0.0"))
        assertFalse(isNewer("nightly", "1.0.0"))
    }

    @Test
    fun `short and long tags line up`() {
        assertTrue(isNewer("v2", "1.9.9"))
        assertFalse(isNewer("v1", "1.0.0"))
    }
}
