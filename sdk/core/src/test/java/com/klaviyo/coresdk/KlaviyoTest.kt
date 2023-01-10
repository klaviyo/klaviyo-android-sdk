package com.klaviyo.coresdk

import android.content.Context
import com.klaviyo.coresdk.networking.UserInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class KlaviyoTest {
    private val contextMock: Context = mockk()
    private val spyKlaviyo: Klaviyo = spyk(Klaviyo).also {
        // TODO isolation of our services would make testing easier, e.g. Dependency injection
        every { it.createIdentifyRequest(any()) } returns Unit
        every { it.createEventRequest(any(), any(), any()) } returns Unit
    }

    @Before
    fun setup() {
        spyKlaviyo.initialize(
            apiKey = "Fake_Key",
            applicationContext = contextMock
        )
    }

    @Test
    fun `Klaviyo Configure API sets variables successfully`() {
        spyKlaviyo.initialize(
            "Fake_Key",
            contextMock
        )

        assert(KlaviyoConfig.apiKey == "Fake_Key")
        assert(KlaviyoConfig.applicationContext == contextMock)
    }

    @Test
    fun `Sets user external ID into info`() {
        val id = "abc"
        spyKlaviyo.setExternalId(id)

        assert(UserInfo.externalId == id)
        verify(exactly = 1) { spyKlaviyo.createIdentifyRequest(any()) }
    }

    @Test
    fun `Sets user email into info`() {
        val email = "test@test.com"
        spyKlaviyo.setEmail(email)

        assert(UserInfo.email == email)
        verify(exactly = 1) { spyKlaviyo.createIdentifyRequest(any()) }
    }

    @Test
    fun `Sets user phone into info`() {
        val phone = "802-555-5555"
        spyKlaviyo.setPhoneNumber(phone)

        assert(UserInfo.phoneNumber == phone)
        verify(exactly = 1) { spyKlaviyo.createIdentifyRequest(any()) }
    }

    @Test
    fun `Resets user info`() {
        UserInfo.email = "test"
        UserInfo.phoneNumber = "test"
        UserInfo.externalId = "test"

        spyKlaviyo.resetProfile()

        assert(UserInfo.email == "")
        assert(UserInfo.phoneNumber == "")
        assert(UserInfo.externalId == "")
        // TODO API behavior... it should probably have made 1 call with new anonymous ID?
    }
}
