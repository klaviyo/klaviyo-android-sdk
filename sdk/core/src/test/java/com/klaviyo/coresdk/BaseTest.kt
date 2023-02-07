package com.klaviyo.coresdk

import android.content.Context
import android.os.Build
import com.klaviyo.coresdk.config.Config
import com.klaviyo.coresdk.config.StaticClock
import com.klaviyo.coresdk.lifecycle.LifecycleMonitor
import com.klaviyo.coresdk.model.InMemoryDataStore
import com.klaviyo.coresdk.networking.ApiClient
import com.klaviyo.coresdk.networking.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import org.junit.After
import org.junit.Before

/**
 * Base class for re-usable mocks and stubs
 */
internal abstract class BaseTest {
    companion object {
        const val API_KEY = "stub_public_api_key"
        const val EMAIL = "test@domain.com"
        const val PHONE = "+12223334444"
        const val EXTERNAL_ID = "abcdefg"
        const val ANON_ID = "anonId123"

        const val TIME = 1234567890000L
        const val ISO_TIME = "2009-02-13T23:31:30+0000"
    }

    protected val contextMock = mockk<Context>()

    protected val configMock = mockk<Config>().apply {
        every { apiKey } returns API_KEY
        every { applicationContext } returns contextMock
        every { clock } returns StaticClock(TIME, ISO_TIME)
    }
    protected val lifecycleMonitorMock = mockk<LifecycleMonitor>()
    protected val networkMonitorMock = mockk<NetworkMonitor>()
    protected val dataStoreSpy = spyk(InMemoryDataStore)
    protected val apiClientMock = mockk<ApiClient>()

    @Before
    open fun setup() {
        // Mock Registry by default to encourage unit tests to be decoupled from other services
        mockkObject(Klaviyo.Registry)
        every { Klaviyo.Registry.config } returns configMock
        every { Klaviyo.Registry.lifecycleMonitor } returns lifecycleMonitorMock
        every { Klaviyo.Registry.networkMonitor } returns networkMonitorMock
        every { Klaviyo.Registry.dataStore } returns dataStoreSpy
        every { Klaviyo.Registry.apiClient } returns apiClientMock

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
        unmockkObject(Klaviyo.Registry)
    }
}