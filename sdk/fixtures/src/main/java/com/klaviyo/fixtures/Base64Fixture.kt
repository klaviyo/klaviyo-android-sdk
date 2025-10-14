package com.klaviyo.fixtures

import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.Base64 as JavaBase64

/**
 * Fixture to mock android.util.Base64 with java.util.Base64 for unit tests
 * This allows tests to run without requiring the Android framework
 */
@RequiresApi(Build.VERSION_CODES.O)
fun mockBase64() {
    mockkStatic(Base64::class)

    // Mock encodeToString to use Java's Base64 encoder
    every {
        Base64.encodeToString(any<ByteArray>(), any<Int>())
    } answers {
        val bytes = firstArg<ByteArray>()
        JavaBase64.getEncoder().encodeToString(bytes)
    }

    // Mock encode to use Java's Base64 encoder
    every {
        Base64.encode(any<ByteArray>(), any<Int>())
    } answers {
        val bytes = firstArg<ByteArray>()
        JavaBase64.getEncoder().encode(bytes)
    }

    // Mock decode to use Java's Base64 decoder
    every {
        Base64.decode(any<String>(), any<Int>())
    } answers {
        val str = firstArg<String>()
        JavaBase64.getDecoder().decode(str)
    }

    // Mock decode for byte array input
    every {
        Base64.decode(any<ByteArray>(), any<Int>())
    } answers {
        val bytes = firstArg<ByteArray>()
        JavaBase64.getDecoder().decode(bytes)
    }
}

fun unmockBase64() {
    unmockkStatic(Base64::class)
}
