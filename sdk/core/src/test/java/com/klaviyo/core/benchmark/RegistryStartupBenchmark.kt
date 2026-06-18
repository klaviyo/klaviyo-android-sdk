package com.klaviyo.core.benchmark

import com.klaviyo.core.Registry
import java.lang.management.ManagementFactory

/**
 * A unique marker type used only to drive a first-of-its-kind reified Registry
 * call. It must never be registered anywhere else so that [main] always
 * exercises the very first registration of this type in the process.
 */
private interface RegistryStartupBenchmarkMarker

/**
 * Standalone entrypoint, launched in a FRESH JVM by [RegistryStartupBenchmarkTest].
 *
 * It measures what the FIRST reified [Registry] call costs in a cold process — the
 * exact work `FormsInitProvider.onCreate()` performs on the main thread at app
 * startup (MAGE-805):
 * - On master the call routes through `kotlin.reflect.typeOf<T>()`, whose first
 *   invocation cold-initializes the Kotlin reflection subsystem
 *   (RuntimeModuleData / DeserializationComponentsForJava / JvmBuiltIns). That
 *   class-loads the entire reflection descriptor stack — hundreds of classes.
 * - With the MAGE-805 fix the call routes through `T::class.java` (a direct class
 *   literal) and loads only a handful of classes — no reflection subsystem.
 *
 * The primary signal is the number of classes loaded during the call, captured via
 * [ManagementFactory]'s class-loading bean. Class count is deterministic and
 * hardware-independent, unlike wall-clock time (CI runners are far faster than the
 * low-end devices where the ANR reproduces), so it gives a stable red/green split.
 * Elapsed time is also reported for context.
 *
 * A fresh JVM is required because the reflection subsystem is process-global and
 * warms up exactly once; the cost is invisible in the already-warmed test JVM.
 *
 * Output contract (stdout): a `CLASSES_LOADED=<n>` line and an `ELAPSED_NS=<n>` line.
 */
fun main() {
    val classLoading = ManagementFactory.getClassLoadingMXBean()

    // Warm up the measurement/printing machinery first so the measured delta is
    // attributable to the Registry call rather than incidental class loading.
    println("WARMUP=${classLoading.totalLoadedClassCount}")

    val loadedBefore = classLoading.totalLoadedClassCount
    val start = System.nanoTime()
    // First reified Registry call in this process — the cold-start hot path.
    // registerOnce -> isRegistered -> key derivation: typeOf<T>() on master
    // (reflection cold init), T::class.java with the MAGE-805 fix (no reflection).
    Registry.registerOnce<RegistryStartupBenchmarkMarker> {
        object : RegistryStartupBenchmarkMarker {}
    }
    val elapsedNs = System.nanoTime() - start
    val classesLoaded = classLoading.totalLoadedClassCount - loadedBefore

    println("CLASSES_LOADED=$classesLoaded")
    println("ELAPSED_NS=$elapsedNs")
}
