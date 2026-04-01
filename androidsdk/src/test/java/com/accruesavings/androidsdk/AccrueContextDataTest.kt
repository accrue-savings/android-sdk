package com.accruesavings.androidsdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccrueContextDataTest {
    @Test
    fun stableReferenceId_defaultsToNullOnUserData() {
        assertNull(AccrueUserData().stableReferenceId)
    }

    @Test
    fun stableReferenceId_isOnUserData() {
        val userData = AccrueUserData(stableReferenceId = "ref-stable-1")
        assertEquals("ref-stable-1", userData.stableReferenceId)
        assertEquals("ref-stable-1", AccrueContextData(userData = userData).userData.stableReferenceId)
    }
}
