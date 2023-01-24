package com.klaviyo.coresdk

import android.content.Context
import com.klaviyo.coresdk.helpers.InMemoryDataStore
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey
import com.klaviyo.coresdk.networking.UserInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class KlaviyoTest {
    private val contextMock: Context = mockk()

    @Before
    fun setup() {
        mockkObject(Klaviyo.Registry)
        every { Klaviyo.Registry.dataStore } returns spyk(InMemoryDataStore)
        every { Klaviyo.Registry.apiClient } returns mockk()
        every { Klaviyo.Registry.apiClient.enqueueProfile(any()) } returns Unit
        every { Klaviyo.Registry.apiClient.enqueueEvent(any(), any(), any()) } returns Unit

        Klaviyo.initialize(
            apiKey = "Fake_Key",
            applicationContext = contextMock
        )
    }

    @Test
    fun `Klaviyo Configure API sets variables successfully`() {
        Klaviyo.initialize(
            "Fake_Key",
            contextMock
        )

        assert(KlaviyoConfig.apiKey == "Fake_Key")
        assert(KlaviyoConfig.applicationContext == contextMock)
    }

    @Test
    fun `Sets user external ID into info`() {
        val id = "abc"
        Klaviyo.setExternalId(id)

        assert(UserInfo.externalId == id)
        verify(exactly = 1) { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Sets user email into info`() {
        val email = "test@test.com"
        Klaviyo.setEmail(email)

        assert(UserInfo.email == email)
        verify(exactly = 1) { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Sets user phone into info`() {
        val phone = "802-555-5555"
        Klaviyo.setPhoneNumber(phone)

        assert(UserInfo.phoneNumber == phone)
        verify(exactly = 1) { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Sets an arbitrary user property`() {
        // TODO improve this test.
        Klaviyo.setProfileAttribute(KlaviyoProfileAttributeKey.FIRST_NAME, "evan")

        verify(exactly = 1) { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
    }

    @Test
    fun `Resets user info`() {
        UserInfo.email = "test"
        UserInfo.phoneNumber = "test"
        UserInfo.externalId = "test"

        Klaviyo.resetProfile()

        assert(UserInfo.email == "")
        assert(UserInfo.phoneNumber == "")
        assert(UserInfo.externalId == "")

        verify(exactly = 1) { Klaviyo.Registry.apiClient.enqueueProfile(any()) }
    }
}
