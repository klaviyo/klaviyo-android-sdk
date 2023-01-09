package com.klaviyo.coresdk

import android.content.Context
import com.klaviyo.coresdk.networking.UserInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Before
import org.junit.Test

class KlaviyoTest {
    private val contextMock: Context = mockk()
    private val spyKlaviyo: Klaviyo = spyk(Klaviyo).also {
        // Cheap way to mock API calls
        // TODO better isolation of our SDK's services would make testing easier
        every { it.setProfile(any()) } returns Klaviyo
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
    fun `Sets user email into info`() {
        val email = "test@test.com"
        spyKlaviyo.setEmail(email)

        assert(UserInfo.email == email)
    }

    @Test
    fun `Sets user phone into info`() {
        val phone = "802-555-5555"
        spyKlaviyo.setPhoneNumber(phone)

        assert(UserInfo.phone == phone)
    }

    @Test
    fun `Sets user external ID into info`() {
        val id = "abc"
        spyKlaviyo.setExternalId(id)

        assert(UserInfo.external_id == id)
    }

    @Test
    fun `Resets user info`() {
        UserInfo.email = "test"
        UserInfo.phone = "test"
        UserInfo.external_id = "test"

        spyKlaviyo.resetProfile()

        assert(UserInfo.email == "")
        assert(UserInfo.phone == "")
        assert(UserInfo.external_id == "")
    }
}
