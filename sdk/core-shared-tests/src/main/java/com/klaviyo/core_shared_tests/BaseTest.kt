package com.klaviyo.core_shared_tests // ktlint-disable package-name

import android.content.Context
import android.os.Build
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.networking.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import java.lang.reflect.Field
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
    }

    protected val contextMock = mockk<Context>()

    protected val configMock = mockk<Config>().apply {
        every { apiKey } returns API_KEY
        every { applicationContext } returns contextMock
        every { networkMaxRetries } returns 4
        every { networkFlushIntervals } returns intArrayOf(10_000, 30_000, 60_000)
        every { baseUrl } returns "https://test.fake-klaviyo.com"
    }
    protected val lifecycleMonitorMock = mockk<LifecycleMonitor>()
    protected val networkMonitorMock = mockk<NetworkMonitor>()
    protected val dataStoreSpy = spyk(InMemoryDataStore())
    protected val logMock = spyk(TestLogger())

    @Before
    open fun setup() {
        // Mock Registry by default to encourage unit tests to be decoupled from other services
        mockkObject(Registry)
        every { Registry.config } returns configMock
        every { Registry.lifecycleMonitor } returns lifecycleMonitorMock
        every { Registry.networkMonitor } returns networkMonitorMock
        every { Registry.dataStore } returns dataStoreSpy
        every { Registry.clock } returns StaticClock(TIME, ISO_TIME)
        every { Registry.log } returns logMock

        // Mock using latest SDK
        setFinalStatic(Build.VERSION::class.java.getField("SDK_INT"), 33)
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
        val modifiersField: Field = Field::class.java.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
        field.set(null, newValue)
    }

    @After
    open fun clear() {
        unmockkObject(Registry)
    }
}
