package com.example.apps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppControlStoreTest {
    private lateinit var store: AppControlStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_control_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = AppControlStore(context)
    }

    @Test
    fun launchCountAndTimestampAccumulate() {
        store.recordAppLaunch("com.example.first", launchedAt = 100L)
        store.recordAppLaunch("com.example.first", launchedAt = 200L)

        assertEquals(2, store.getLaunchCount("com.example.first"))
        assertEquals(200L, store.getLastLaunchTime("com.example.first"))
    }

    @Test
    fun activeFirewallStatePersistsWhitelistAndEnabledFlagTogether() {
        assertFalse(store.isFirewallEnabled())
        assertTrue(store.getNetworkWhitelist().isEmpty())

        store.saveActiveFirewallState(
            enabled = true,
            packageNames = setOf("com.example.allowed", "com.example.other")
        )

        assertTrue(store.isFirewallEnabled())
        assertEquals(
            setOf("com.example.allowed", "com.example.other"),
            store.getNetworkWhitelist()
        )
    }
}