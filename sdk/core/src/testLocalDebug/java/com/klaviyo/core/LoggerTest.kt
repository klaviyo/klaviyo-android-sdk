package com.klaviyo.core

import android.util.MockLog
import com.klaviyo.fixtures.BaseTest
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Test

class LoggerTest : BaseTest() {

    private val stubMsg = "test"
    private val expectedTag = "LoggerTest"

    override fun setup() {
        mockkStatic(MockLog::class)
    }

    @Test
    fun `Invoke console verbose`() {
        Logger().verbose(stubMsg)
        verify { MockLog.v(any(), stubMsg, null) }
    }

    @Test
    fun `Invoke console debug`() {
        Logger().debug(stubMsg)
        verify { MockLog.d(any(), stubMsg, null) }
    }

    @Test
    fun `Invoke console info`() {
        Logger().info(stubMsg)
        verify { MockLog.i(any(), stubMsg, null) }
    }

    @Test
    fun `Invoke console error`() {
        Logger().error(stubMsg)
        verify { MockLog.e(any(), stubMsg, null) }
    }

    @Test
    fun wtf() {
        val ex = Exception("error")
        Logger().wtf(stubMsg, ex)
        verify { MockLog.wtf(expectedTag, stubMsg, ex) }
    }

    @Test
    fun `Determines enclosing class as tag`() {
        Logger().debug("")
        verify { MockLog.d(expectedTag, any(), null) }
    }
}
