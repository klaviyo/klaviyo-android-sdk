package com.klaviyo.core

import com.klaviyo.core.config.Log.Level
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class KLogTest {

    @Before
    fun setup() {
        mockkStatic("com.klaviyo.core.LogLevelKt")
        for (level in Level.entries) {
            every { level.log(any(), any(), any()) } answers {
                logMock(level.name, arg<String>(1), arg<String>(2), arg<Throwable?>(3))
            }
        }
        every { Level.None.log(any(), any(), any()) } answers {
            throw IllegalStateException("Level.None.log should not be called")
        }
    }

    private fun logMock(level: String, tag: String, msg: String, ex: Throwable?): Int {
        println("$level: $tag: $msg: $ex")
        return 0
    }

    @Test
    fun `Log level is obeyed`() {
        for (level in Level.entries) {
            setup()

            KLog.logLevel = level

            KLog.verbose("verbose")
            verify(inverse = level == Level.None || level.ordinal > Level.Verbose.ordinal) {
                Level.Verbose.log(any(), any(), any())
            }

            KLog.debug("debug")
            verify(inverse = level == Level.None || level.ordinal > Level.Debug.ordinal) {
                Level.Debug.log(any(), any(), any())
            }

            KLog.info("info")
            verify(inverse = level == Level.None || level.ordinal > Level.Info.ordinal) {
                Level.Info.log(any(), any(), any())
            }

            KLog.warning("warning")
            verify(inverse = level == Level.None || level.ordinal > Level.Warning.ordinal) {
                Level.Warning.log(any(), any(), any())
            }

            KLog.error("error")
            verify(inverse = level == Level.None || level.ordinal > Level.Error.ordinal) {
                Level.Error.log(any(), any(), any())
            }

            KLog.wtf("wtf")
            verify(inverse = level == Level.None || level.ordinal > Level.Assert.ordinal) {
                Level.Assert.log(any(), any(), any())
            }
        }
    }

    @Test
    fun `Log tag contains Klaviyo`() {
        KLog.logLevel = Level.Verbose
        KLog.verbose("msg")
        verify { Level.Verbose.log(match { it.contains("Klaviyo") }, any(), any()) }
    }
}
