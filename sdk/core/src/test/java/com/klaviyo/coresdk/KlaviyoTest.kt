package com.klaviyo.coresdk

import com.klaviyo.coresdk.networking.*
import org.junit.Test

class KlaviyoTest {
    @Test
    fun `Sets user email into info`() {
        val email = "test@test.com"
        Klaviyo.setUserEmail(email)

        assert(UserInfo.email == email)
    }
}