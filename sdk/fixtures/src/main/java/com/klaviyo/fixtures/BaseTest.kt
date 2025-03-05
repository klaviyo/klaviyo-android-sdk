package com.klaviyo.fixtures

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.view.View
import android.view.ViewGroup
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.networking.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import java.io.ByteArrayInputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before

/**
 * Base class for re-usable mocks and stubs
 */
abstract class BaseTest {
    companion object {
        const val API_KEY = "stub_public_api_key"
        const val EMAIL = "test@domain.com"
        const val PHONE = "+12223334444"
        const val EXTERNAL_ID = "abcdefg"
        const val ANON_ID = "anonId123"
        const val PUSH_TOKEN = "abcdefghijklmnopqrstuvwxyz"

        const val TIME = 1234567890000L
        const val ISO_TIME = "2009-02-13T23:31:30+0000"

        /**
         * Test helper method for comparing JSONObjects
         *
         * @param first
         * @param second
         */
        fun compareJson(first: JSONObject, second: JSONObject) {
            val firstKeys = arrayListOf<String>()
            val secondKeys = arrayListOf<String>()
            first.keys().forEach { firstKeys.add(it) }
            second.keys().forEach { secondKeys.add(it) }

            assertEquals(firstKeys.sorted(), secondKeys.sorted())

            first.keys().forEach { k ->
                if (first[k] is JSONObject && second[k] is JSONObject) {
                    compareJson(first[k] as JSONObject, second[k] as JSONObject)
                } else {
                    assertEquals(first[k], second[k])
                }
            }
        }

        val htmlString = """
            <!DOCTYPE html>
            <html lang="en">
            <head data-sdk-name="SDK_NAME"
                  data-sdk-version="SDK_VERSION"
                  data-native-bridge-name="BRIDGE_NAME"
                  data-native-bridge-handshake='BRIDGE_HANDSHAKE'
            >
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0, viewport-fit=cover"/>
                <meta name="referrer" content="same-origin" /> <!--  This meta tag protects @imported fonts from being blocked by CORS  -->
                <title>Klaviyo In-App Form Template</title>
                <link rel="stylesheet" type="text/css" href="https://static-forms.klaviyo.com/fonts/api/v1/in-app-web-fonts/websafe_fonts.css" crossorigin/>
                <script type="text/javascript" src="KLAVIYO_JS_URL"></script>
            </head>
            <body></body>
            </html>
        """.trimIndent()
    }

    protected val mockApplicationInfo = mockk<ApplicationInfo>()
    protected val mockPackageManager = mockk<PackageManager>()
    protected val mockAssets = mockk<AssetManager> {
        every { open("InAppFormsTemplate.html") } returns
            ByteArrayInputStream(htmlString.encodeToByteArray())
    }

    protected val mockContext = mockk<Context>().apply {
        every { applicationInfo } returns mockApplicationInfo
        every { packageManager } returns mockPackageManager
        every { assets } returns mockAssets
    }

    protected val debounceTime = 5
    protected val mockConfig = mockk<Config>().apply {
        every { apiKey } returns API_KEY
        every { applicationContext } returns mockContext
        every { debounceInterval } returns debounceTime
        every { networkMaxAttempts } returns 50
        every { networkTimeout } returns 1000
        every { networkMaxRetryInterval } returns 180_000L
        every { networkFlushIntervals } returns longArrayOf(10_000, 30_000, 60_000)
        every { networkJitterRange } returns 0..0
        every { baseUrl } returns "https://test.fake-klaviyo.com"
        every { apiRevision } returns "1234-56-78"
        every { baseCdnUrl } returns "https://decent.cdn.url.com"
        every { assetSource } returns null
        every { sdkName } returns "klaviyo-android-sdk"
        every { sdkVersion } returns "4.20.69"
    }
    protected val mockContentView: ViewGroup = mockk(relaxed = true)
    protected val mockDecorView: View = mockk(relaxed = true) {
        every { findViewById<ViewGroup>(any()) } returns mockContentView
    }
    protected val mockActivity: Activity = mockk(relaxed = true) {
        every { window.decorView } returns mockDecorView
    }
    protected val mockLifecycleMonitor = mockk<LifecycleMonitor>().apply {
        every { onActivityEvent(any()) } returns Unit
        every { offActivityEvent(any()) } returns Unit
        every { currentActivity } returns mockActivity
    }
    protected val mockNetworkMonitor = mockk<NetworkMonitor>()
    protected val spyDataStore = spyk(InMemoryDataStore())
    protected val spyLog = spyk(LogFixture())
    protected val staticClock = StaticClock(TIME, ISO_TIME)

    @Before
    open fun setup() {
        // Mock Registry by default to encourage unit tests to be decoupled from other services
        mockkObject(Registry)
        every { Registry.config } returns mockConfig
        every { Registry.lifecycleMonitor } returns mockLifecycleMonitor
        every { Registry.networkMonitor } returns mockNetworkMonitor
        every { Registry.dataStore } returns spyDataStore
        every { Registry.clock } returns staticClock
        every { Registry.log } returns spyLog

        // Mock using latest SDK
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 33)
    }

    @After
    open fun cleanup() {
        unmockkObject(Registry)
    }

    /**
     * Gross way to modify a final static field through reflection
     *
     * @param field
     * @param newValue
     */
    @Throws(Exception::class)
    protected fun setFinalStatic(field: Field, newValue: Any?) {
        field.isAccessible = true

        getModifiersField().also {
            it.isAccessible = true
            it.set(field, field.modifiers and Modifier.FINAL.inv())
        }
        field.set(null, newValue)
    }

    private fun getModifiersField(): Field = try {
        Field::class.java.getDeclaredField("modifiers")
    } catch (e: NoSuchFieldException) {
        try {
            val getDeclaredFields0: Method = Class::class.java.getDeclaredMethod(
                "getDeclaredFields0",
                Boolean::class.javaPrimitiveType
            )
            getDeclaredFields0.isAccessible = true
            @Suppress("unchecked_cast")
            (getDeclaredFields0.invoke(Field::class.java, false) as Array<Field>).first { it.name == "modifiers" }
        } catch (ex: ReflectiveOperationException) {
            e.addSuppressed(ex)
            throw e
        }
    }
}
