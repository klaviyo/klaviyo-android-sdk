package com.klaviyo.core.utils

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import io.mockk.EqMatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThreadHelperTest {
    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        mockkConstructor(Handler::class)
        mockkConstructor(HandlerThread::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
        unmockkConstructor(Handler::class)
        unmockkConstructor(HandlerThread::class)
    }

    @Test
    fun `runOnUiThread runs block immediately on main thread`() {
        val mainLooper = mockk<Looper>()
        every { Looper.getMainLooper() } returns mainLooper
        every { Looper.myLooper() } returns mainLooper

        var ran = false
        KlaviyoThreadHelper.runOnUiThread { ran = true }

        assert(ran) {
            "Block should have run immediately on main thread"
        }
    }

    @Test
    fun `runOnUiThread posts block when not on main thread`() {
        val mainLooper = mockk<Looper>()
        val otherLooper = mockk<Looper>()
        every { Looper.getMainLooper() } returns mainLooper
        every { Looper.myLooper() } returns otherLooper

        every { constructedWith<Handler>(EqMatcher(mainLooper, true)).post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }

        var ran = false
        KlaviyoThreadHelper.runOnUiThread { ran = true }

        assert(ran) {
            "Block should have run after being posted to main thread"
        }
    }

    @Test
    fun `getHandlerThread returns a HandlerThread for that name`() {
        val name = "TestThread"
        every { constructedWith<HandlerThread>(EqMatcher(name, true)).name } returns name
        val result = KlaviyoThreadHelper.getHandlerThread(name)
        assertEquals(name, result.name)
    }
}
