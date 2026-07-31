package com.whocalled.android

import com.whocalled.android.data.stableAnonymousDeviceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceIdentityTest {

    @Test
    fun sameAppScopedAndroidIdProducesSameAnonymousId() {
        val first = stableAnonymousDeviceId("com.devfi.whocalled", "device-user-signing-key")
        val afterReinstall = stableAnonymousDeviceId("com.devfi.whocalled", "device-user-signing-key")

        assertEquals(first, afterReinstall)
    }

    @Test
    fun identifierRemainsScopedToTheApplication() {
        val whoCalled = stableAnonymousDeviceId("com.devfi.whocalled", "same-device")
        val anotherApp = stableAnonymousDeviceId("com.example.other", "same-device")

        assertNotEquals(whoCalled, anotherApp)
    }

    @Test
    fun invalidLegacyAndroidIdFallsBackToStoredRandomId() {
        assertNull(stableAnonymousDeviceId("com.devfi.whocalled", "9774d56d682e549c"))
    }
}
