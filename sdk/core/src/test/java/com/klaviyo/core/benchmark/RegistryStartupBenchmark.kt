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
 * The primary signal is the number of classes loaded during the call (see
 * [totalLoadedClasses]). Class count is deterministic and hardware-independent,
 * unlike wall-clock time (CI runners are far faster than the low-end devices where
 * the ANR reproduces), so it gives a stable red/green split. Elapsed time is also
 * reported for context.
 *
 * A fresh JVM is required because the reflection subsystem is process-global and
 * warms up exactly once; the cost is invisible in the already-warmed test JVM.
 *
 * Output contract (stdout): a `CLASSES_LOADED=<n>` line and an `ELAPSED_NS=<n>` line.
 */
fun main() {
    // Warm up the measurement/printing machinery first (including the reflective
    // ManagementFactory lookup) so the measured delta is attributable to the
    // Registry call rather than incidental class loading.
    println("WARMUP=${totalLoadedClasses()}")

    val loadedBefore = totalLoadedClasses()
    val start = System.nanoTime()
    // First reified Registry call in this process — the cold-start hot path.
    // registerOnce -> isRegistered -> key derivation: typeOf<T>() on master
    // (reflection cold init), T::class.java with the MAGE-805 fix (no reflection).
    Registry.registerOnce<RegistryStartupBenchmarkMarker> {
        object : RegistryStartupBenchmarkMarker {}
    }
    val elapsedNs = System.nanoTime() - start
    val classesLoaded = totalLoadedClasses() - loadedBefore

    println("CLASSES_LOADED=$classesLoaded")
    println("ELAPSED_NS=$elapsedNs")
}

/**
 * Total number of classes this JVM has loaded so far, read from
 * `java.lang.management.ClassLoadingMXBean`.
 *
 * Accessed reflectively on purpose: the `java.lang.management` package is NOT on
 * the Android unit-test COMPILE classpath (Android's `android.jar` omits it), so a
 * direct import does not compile. It IS present at RUNTIME in the forked JDK that
 * runs this `main`, so reflection resolves it there. Methods are invoked via the
 * exported `ClassLoadingMXBean` interface to avoid JPMS access issues with the
 * (non-exported) bean implementation class.
 */
private fun totalLoadedClasses(): Long {
    val managementFactory = Class.forName("java.lang.management.ManagementFactory")
    val bean = managementFactory.getMethod("getClassLoadingMXBean").invoke(null)
    val beanInterface = Class.forName("java.lang.management.ClassLoadingMXBean")
    return beanInterface.getMethod("getTotalLoadedClassCount").invoke(bean) as Long
}
