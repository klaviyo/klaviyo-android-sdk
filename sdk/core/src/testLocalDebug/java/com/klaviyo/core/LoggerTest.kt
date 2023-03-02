package com.klaviyo.core

import com.klaviyo.core_shared_tests.BaseTest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Test

class LoggerTest : BaseTest() {

    private val stubMsg = "test"
    private val expectedTag = "LoggerTest"

    override fun setup() {
        mockkObject(Console)
        every { Console.log(any(), any(), any(), any()) } returns 0
    }

    @Test
    fun `Invoke console debug`() {
        Logger().debug(stubMsg)
        verify { Console.log(stubMsg, Console.Level.Debug, any(), null) }
    }

    @Test
    fun `Invoke console info`() {
        Logger().info(stubMsg)
        verify { Console.log(stubMsg, Console.Level.Info, any(), null) }
    }

    @Test
    fun `Invoke console error`() {
        Logger().error(stubMsg)
        verify { Console.log(stubMsg, Console.Level.Error, any(), null) }
    }

    @Test
    fun wtf() {
        Logger().wtf(stubMsg)
        verify { Console.log(stubMsg, Console.Level.Assert, any(), null) }
    }

    @Test
    fun `Determines enclosing class as tag`() {
        Logger().debug(stubMsg)
        verify { Console.log(stubMsg, any(), expectedTag, null) }
    }
}