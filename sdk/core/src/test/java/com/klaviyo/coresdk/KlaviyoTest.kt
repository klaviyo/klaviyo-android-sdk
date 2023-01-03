package com.klaviyo.coresdk

import android.app.Application
import android.content.Context
import com.klaviyo.coresdk.networking.UserInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class KlaviyoTest {
    private val contextMock: Context = mockk()
    private val appMock: Application = mockk {
        every { applicationContext } returns contextMock
        every { registerActivityLifecycleCallbacks(any()) } returns Unit
    }

    @Test
    fun `Klaviyo Configure API sets variables successfully`() {
        Klaviyo.configure(
            "Fake_Key",
            appMock,
            1000,
            10000,
            10,
            1000,
            false
        )

        assert(KlaviyoConfig.apiKey == "Fake_Key")
        assert(KlaviyoConfig.networkTimeout == 1000)
        assert(KlaviyoConfig.networkFlushInterval == 10000)
        assert(KlaviyoConfig.networkFlushDepth == 10)
        assert(KlaviyoConfig.networkFlushCheckInterval == 1000)
        assert(!KlaviyoConfig.networkUseAnalyticsBatchQueue)
        verify(exactly = 1) {
            appMock.registerActivityLifecycleCallbacks(any())
        }
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
        Klaviyo.setPhone(phone)

        assert(UserInfo.phone == phone)
    }

    @Test
    fun `Resets user info`() {
        UserInfo.email = "test"
        UserInfo.phone = "test"

        Klaviyo.reset()

        assert(UserInfo.email == "")
        assert(UserInfo.phone == "")
    }
}
