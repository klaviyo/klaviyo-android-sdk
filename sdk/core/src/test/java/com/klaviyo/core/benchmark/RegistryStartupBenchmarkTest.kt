package com.klaviyo.core.benchmark

import java.io.File
import org.junit.Test

/**
 * Cold-start regression test for MAGE-805.
 *
 * Launches a FRESH JVM (see [RegistryStartupBenchmark.main]) and asserts that the
 * first reified [com.klaviyo.core.Registry] call — the work `FormsInitProvider`
 * runs on the main thread at app startup — does NOT cold-initialize the Kotlin
 * reflection subsystem.
 *
 * The discriminating signal is the number of classes loaded during that first call:
 * - On master the call routes through `kotlin.reflect.typeOf<T>()`, which cold-inits
 *   the reflection descriptor stack and class-loads hundreds of classes → this test
 *   FAILS (red).
 * - With the MAGE-805 `Class<*>` fix the call routes through `T::class.java` and
 *   loads only a handful → this test PASSES (green).
 *
 * Class-load count is used instead of wall-clock time because it is deterministic
 * and hardware-independent (CI runners are far faster than the low-end devices where
 * the ANR reproduces). A fresh process is required because the reflection subsystem
 * is process-global and warms up once.
 *
 * The [MAX_CLASSES_LOADED] ceiling sits in the wide gap between the two paths; the
 * failure message reports the observed count so the threshold can be tuned from CI.
 */
class RegistryStartupBenchmarkTest {

    private companion object {
        /**
         * Upper bound on classes loaded by the first Registry call. The fixed path
         * loads a few dozen at most; the reflection cold-init path loads several
         * hundred. This sits between them with margin on both sides.
         */
        const val MAX_CLASSES_LOADED = 100L
    }

    @Test
    fun `first Registry call must not cold-init kotlin reflect at startup`() {
        val javaBin = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
        val classpath = System.getProperty("java.class.path")
        val mainClass = "com.klaviyo.core.benchmark.RegistryStartupBenchmarkKt"

        val process = ProcessBuilder(javaBin, "-cp", classpath, mainClass)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        println("[RegistryStartupBenchmark] subprocess exit=$exitCode, output:\n$output")

        val classesLoaded = output.metric("CLASSES_LOADED")
            ?: throw AssertionError(
                "Subprocess emitted no CLASSES_LOADED (exit=$exitCode).\nOutput:\n$output"
            )
        val elapsedMs = (output.metric("ELAPSED_NS") ?: 0L) / 1_000_000.0

        println(
            "[RegistryStartupBenchmark] first Registry reified call (cold process): " +
                "$classesLoaded classes loaded, ${elapsedMs}ms"
        )

        if (classesLoaded > MAX_CLASSES_LOADED) {
            throw AssertionError(
                "First Registry call loaded $classesLoaded classes (max $MAX_CLASSES_LOADED), " +
                    "indicating kotlin.reflect cold init on the cold-start path (MAGE-805). " +
                    "Took ${elapsedMs}ms. The Class<*>-keyed Registry should load far fewer."
            )
        }
    }

    private fun String.metric(key: String): Long? = lineSequence()
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("$key=")
        ?.trim()
        ?.toLongOrNull()
}
