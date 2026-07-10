package com.klaviyo.core

import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class SdkFeaturesTest : BaseTest() {

    /**
     * Simulate a host declaring [manifestKey] in the manifest with the given boolean [value].
     * Absent keys are left at BaseTest's defaults (hasManifestKey → false).
     */
    private fun stubManifestKey(manifestKey: String, value: Boolean) {
        every { mockConfig.hasManifestKey(manifestKey) } returns true
        every { mockConfig.getManifestBoolean(manifestKey, false) } returns value
    }

    @Test
    fun `Header omitted when no manifest keys are present`() {
        assertNull(SdkFeatures.headerValue(SdkFeatureScope.PUSH_TOKEN_REGISTRATION))
    }

    @Test
    fun `Reports only auto_push_tracking when only that key is present and true`() {
        stubManifestKey(SdkFeatureKey.AUTO_PUSH_TRACKING.manifestKey, true)
        assertEquals(
            "auto_push_tracking=1;",
            SdkFeatures.headerValue(SdkFeatureScope.PUSH_TOKEN_REGISTRATION)
        )
    }

    @Test
    fun `Reports only auto_push_tracking when only that key is present and false`() {
        stubManifestKey(SdkFeatureKey.AUTO_PUSH_TRACKING.manifestKey, false)
        assertEquals(
            "auto_push_tracking=0;",
            SdkFeatures.headerValue(SdkFeatureScope.PUSH_TOKEN_REGISTRATION)
        )
    }

    @Test
    fun `Reports auto_push_token_forwarding as inverse of the disable flag when disable is true`() {
        stubManifestKey(SdkFeatureKey.AUTO_PUSH_TOKEN_FORWARDING.manifestKey, true)
        assertEquals(
            "auto_push_token_forwarding=0;",
            SdkFeatures.headerValue(SdkFeatureScope.PUSH_TOKEN_REGISTRATION)
        )
    }

    @Test
    fun `Reports auto_push_token_forwarding as inverse of the disable flag when disable is false`() {
        stubManifestKey(SdkFeatureKey.AUTO_PUSH_TOKEN_FORWARDING.manifestKey, false)
        assertEquals(
            "auto_push_token_forwarding=1;",
            SdkFeatures.headerValue(SdkFeatureScope.PUSH_TOKEN_REGISTRATION)
        )
    }

    @Test
    fun `Reports both attributes in deterministic order when both keys are present`() {
        stubManifestKey(SdkFeatureKey.AUTO_PUSH_TRACKING.manifestKey, true)
        stubManifestKey(SdkFeatureKey.AUTO_PUSH_TOKEN_FORWARDING.manifestKey, true)
        assertEquals(
            "auto_push_tracking=1; auto_push_token_forwarding=0;",
            SdkFeatures.headerValue(SdkFeatureScope.PUSH_TOKEN_REGISTRATION)
        )
    }

    @Test
    fun `Omits an absent key even when the other in-scope key is present`() {
        stubManifestKey(SdkFeatureKey.AUTO_PUSH_TRACKING.manifestKey, false)
        // Forwarding-disable key left absent
        assertEquals(
            "auto_push_tracking=0;",
            SdkFeatures.headerValue(SdkFeatureScope.PUSH_TOKEN_REGISTRATION)
        )
    }

    @Test
    fun `Every feature key resolves exactly one scope`() {
        // Guard: a future entry cannot be added without a (non-null) scope, so it can't silently
        // escape the scope filter.
        SdkFeatureKey.entries.forEach { key ->
            assertNotNull(key.scope)
        }
    }

    @Test
    fun `Header name matches the agreed wire contract`() {
        assertEquals("X-Klaviyo-Sdk-Features", SdkFeatures.HEADER_NAME)
    }
}
