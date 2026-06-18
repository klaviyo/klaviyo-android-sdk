package com.klaviyo.core.benchmark

import java.io.File
import org.junit.Test

/**
 * Cold-start performance harness for MAGE-805.
 *
 * This test launches a FRESH JVM (see [RegistryStartupBenchmark.main]) and reports
 * how long the first reified [com.klaviyo.core.Registry] call takes in that cold
 * process. The measured path is the one `FormsInitProvider.onCreate()` runs on the
 * main thread at app startup.
 *
 * Why a fresh process: the Kotlin reflection subsystem is process-global and warms
 * up exactly once. The Gradle test JVM is already heavily warmed (mockk/JUnit use
 * reflection), so the cold-init cost can only be observed in a brand-new process.
 *
 * How to use this harness:
 * - Run it on the benchmark-only branch (master path) to capture the baseline,
 *   which still routes through `kotlin.reflect.typeOf<T>()` and pays the cold-init.
 * - Run it again with the MAGE-805 fix rebased on top — the same call routes
 *   through `T::class.java` and the reported time drops to near zero.
 * - Compare the `[RegistryStartupBenchmark] ...` lines in the two CI logs.
 *
 * Note on magnitude: absolute numbers depend on the host CPU (CI runners are far
 * faster than the low-end devices where the ANR reproduces) and on `kotlin-reflect`
 * being on the test runtime classpath (it is, transitively via mockk). The harness
 * proves the relative delta and that the fixed path performs no reflection cold
 * init; on-device magnitude is covered separately by a Macrobenchmark startup run.
 *
 * This test reports and sanity-checks only — it is intentionally not a hard
 * threshold gate, since the meaningful signal is the before/after comparison.
 */
class RegistryStartupBenchmarkTest {

    @Test
    fun `measure cold-start cost of first Registry reified call in a fresh JVM`() {
        val javaBin = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
        val classpath = System.getProperty("java.class.path")
        val mainClass = "com.klaviyo.core.benchmark.RegistryStartupBenchmarkKt"

        val process = ProcessBuilder(javaBin, "-cp", classpath, mainClass)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        println("[RegistryStartupBenchmark] subprocess exit=$exitCode, output:\n$output")

        val elapsedLine = output.lineSequence().firstOrNull { it.startsWith("ELAPSED_NS=") }
            ?: throw AssertionError(
                "Benchmark subprocess did not emit ELAPSED_NS (exit=$exitCode).\nOutput:\n$output"
            )

        val elapsedNs = elapsedLine.substringAfter("ELAPSED_NS=").trim().toLong()
        val elapsedMs = elapsedNs / 1_000_000.0
        println(
            "[RegistryStartupBenchmark] first Registry reified call (cold process): " +
                "$elapsedMs ms ($elapsedNs ns) — compare this value with vs without the MAGE-805 fix"
        )

        if (elapsedNs <= 0) {
            throw AssertionError("Expected a positive elapsed time, got $elapsedNs ns")
        }
    }
}
