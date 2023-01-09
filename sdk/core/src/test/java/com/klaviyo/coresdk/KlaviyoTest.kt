package com.klaviyo.coresdk

import android.content.Context
import com.klaviyo.coresdk.networking.UserInfo
import io.mockk.mockk
import org.junit.Test

class KlaviyoTest {
    private val contextMock: Context = mockk()

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
    fun `Sets user email into info`() {
        val email = "test@test.com"
        Klaviyo.setEmail(email)

        assert(UserInfo.email == email)
    }

    @Test
    fun `Sets user phone into info`() {
        val phone = "802-555-5555"
        Klaviyo.setPhoneNumber(phone)

        assert(UserInfo.phone == phone)
    }

    @Test
    fun `Resets user info`() {
        UserInfo.email = "test"
        UserInfo.phone = "test"

        Klaviyo.resetProfile()

        assert(UserInfo.email == "")
        assert(UserInfo.phone == "")
    }
}
