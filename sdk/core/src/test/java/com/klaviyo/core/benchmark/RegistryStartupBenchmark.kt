package com.klaviyo.core.benchmark

import com.klaviyo.core.Registry

/**
 * A unique marker type used only to drive a first-of-its-kind reified Registry
 * call. It must never be registered anywhere else so that [main] always
 * exercises the very first registration of this type in the process.
 */
private interface RegistryStartupBenchmarkMarker

/**
 * Standalone entrypoint, launched in a FRESH JVM by [RegistryStartupBenchmarkTest].
 *
 * It measures the wall-clock cost of the FIRST reified [Registry] call in a cold
 * process:
 * - On master this routes through `kotlin.reflect.typeOf<T>()`, whose first
 *   invocation triggers a one-time cold initialization of the Kotlin reflection
 *   subsystem (RuntimeModuleData / DeserializationComponentsForJava / JvmBuiltIns).
 *   This is the work that runs on the main thread inside
 *   `FormsInitProvider.onCreate()` and contributes to cold-start ANRs (MAGE-805).
 * - With the MAGE-805 fix rebased on top, the same call routes through
 *   `T::class.java` (a direct class literal) and pays no reflection cost.
 *
 * A fresh JVM is required because the reflection subsystem is process-global and
 * one-shot: once it has been warmed (by the test runner, mockk, JUnit, etc.) the
 * cold-init cost can no longer be observed. Running this `main` as the entrypoint
 * of a dedicated process guarantees the Registry call below is the first
 * reflection usage in that process.
 *
 * Output contract: prints exactly one line `ELAPSED_NS=<nanos>` to stdout.
 */
fun main() {
    val start = System.nanoTime()
    // First reified Registry call in this process — the cold-start hot path that
    // FormsInitProvider.onCreate() hits. registerOnce -> isRegistered -> the key
    // derivation (typeOf<T>() on master, T::class.java with the fix).
    Registry.registerOnce<RegistryStartupBenchmarkMarker> {
        object : RegistryStartupBenchmarkMarker {}
    }
    val elapsedNs = System.nanoTime() - start
    println("ELAPSED_NS=$elapsedNs")
}
