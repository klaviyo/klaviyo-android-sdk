package com.klaviyo.inbox

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The inbox starts through the ContentProvider the manifest declares, so a host only has to add
 * the dependency. These tests read the merged manifest instead of calling `onCreate` directly,
 * which would pass even if the declaration were dropped.
 */
class InboxManifestInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val initProvider: ProviderInfo?
        get() = packageInfo().providers?.firstOrNull { it.name == InboxInitProvider::class.java.name }

    @Suppress("DEPRECATION")
    private fun packageInfo(): PackageInfo = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PROVIDERS
    )

    @Test
    fun theMergedManifestDeclaresTheInitProvider() {
        assertNotNull("InboxInitProvider is not declared in the merged manifest", initProvider)
    }

    @Test
    fun theInitProviderIsNotExported() {
        assertFalse(initProvider?.exported ?: true)
    }

    @Test
    fun theInitProviderAuthorityIsScopedToTheApplication() {
        assertEquals("${context.packageName}.klaviyo.inbox.init", initProvider?.authority)
    }
}
