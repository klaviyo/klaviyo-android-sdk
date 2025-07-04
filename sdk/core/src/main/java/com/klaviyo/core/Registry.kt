package com.klaviyo.core

import android.app.Application
import android.content.ComponentCallbacks
import com.klaviyo.core.config.Clock
import com.klaviyo.core.config.Config
import com.klaviyo.core.config.KlaviyoConfig
import com.klaviyo.core.config.Log
import com.klaviyo.core.config.SystemClock
import com.klaviyo.core.lifecycle.KlaviyoLifecycleMonitor
import com.klaviyo.core.lifecycle.LifecycleMonitor
import com.klaviyo.core.model.DataStore
import com.klaviyo.core.model.SharedPreferencesDataStore
import com.klaviyo.core.networking.KlaviyoNetworkMonitor
import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.core.utils.KlaviyoThreadHelper
import com.klaviyo.core.utils.ThreadHelper
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class MissingConfig : KlaviyoException("Klaviyo SDK accessed before initializing")
class MissingRegistration(type: KType) : KlaviyoException("No service registered for $type")
class InvalidRegistration(type: KType) : KlaviyoException("Registered service does not match $type")

typealias Registration = () -> Any

/**
 * Services registry for decoupling SDK components
 * Acts as a very basic Service Locator for internal SDK dependencies
 *
 * Core dependencies are defined as properties for ease of access.
 * Dynamic dependencies can be defined and fetched with the add/get methods.
 * At this time I treat all our services as singletons
 *
 * Technical note on the dynamic registry methods:
 * - noinline keyword allows us to store the Registration lambdas
 * - inline methods make it possible to use generics properly here
 * - @PublishedApi is required so that the inlined methods can read/write to private registry
 *   without leaving the type-erased methods fully public
 */
object Registry {

    /**
     * Shortcut to the registered [Config]
     *
     * @throws [MissingConfig] if uninitialized
     */
    val config: Config get() = get()

    /**
     * Access to [Config.Builder] for registering new or updated SDK configuration
     */
    val configBuilder: Config.Builder get() = KlaviyoConfig.Builder()

    val threadHelper: ThreadHelper = KlaviyoThreadHelper

    val clock: Clock get() = SystemClock

    val log: Log get() = KLog

    val lifecycleMonitor: LifecycleMonitor get() = KlaviyoLifecycleMonitor

    val lifecycleCallbacks: Application.ActivityLifecycleCallbacks get() = KlaviyoLifecycleMonitor

    val componentCallbacks: ComponentCallbacks get() = KlaviyoLifecycleMonitor

    val networkMonitor: NetworkMonitor get() = KlaviyoNetworkMonitor

    val dataStore: DataStore get() = SharedPreferencesDataStore

    /**
     * Internal registry of registered service instances
     */
    @PublishedApi
    internal val services = mutableMapOf<KType, Any>()

    /**
     * Internal registry of registered service lambdas
     */
    @PublishedApi
    internal val registry = mutableMapOf<KType, Registration>()

    /**
     * Remove registered service by type, specified by generic parameter
     *
     * @param T - Type, usually an interface, to register under
     */
    inline fun <reified T : Any> unregister() {
        registry.remove(typeOf<T>())
        services.remove(typeOf<T>())
    }

    /**
     * Register a service for a type, specified by generic parameter
     * Typical usage would be to register the singleton implementation of an interface
     *
     * @param T - Type, usually an interface, to register under
     * @param service - The implementation
     */
    inline fun <reified T : Any> register(service: Any) {
        val type = typeOf<T>()
        unregister<T>()
        services[type] = service
    }

    /**
     * Lazily register a service builder for a type, specified by generic parameter
     * Typical usage would be to register a builder method for the implementation of an interface
     *
     * @param T - Type, usually an interface, to register under
     * @param registration - Lambda that returns the implementation
     */
    inline fun <reified T : Any> register(noinline registration: Registration) {
        val type = typeOf<T>()
        unregister<T>()
        registry[type] = registration
    }

    /**
     * Lazily register a service builder for a type, specified by generic parameter
     * Typical usage would be to register a builder method for the implementation of an interface
     * If this service is already registered, a new registration will be ignored
     *
     * @param T - Type, usually an interface, to register under
     * @param registration - Lambda that returns the implementation
     */
    inline fun <reified T : Any> registerOnce(noinline registration: Registration) {
        if (!isRegistered<T>()) {
            val type = typeOf<T>()
            registry[type] = registration
        }
    }

    /**
     * Query whether a service of type is already registered
     *
     * @param T - Type, usually an interface, to register under
     * @return Whether service is registered
     */
    inline fun <reified T : Any> isRegistered(): Boolean = typeOf<T>().let { type ->
        registry.containsKey(type) || services.containsKey(type)
    }

    /**
     * Get a registered service by type, else null
     *
     * @param T - Type of service, usually an interface
     * @return The instance of the service
     */
    inline fun <reified T : Any> getOrNull(): T? {
        val type = typeOf<T>()
        val service: Any? = services[type]

        if (service is T) return service

        return when (val lazyService = registry[type]?.let { it() }) {
            is T -> lazyService.apply { services[type] = lazyService }
            else -> null
        }
    }

    /**
     * Get a registered service by type
     *
     * @param T - Type of service, usually an interface
     * @return The instance of the service
     * @throws MissingRegistration If no service is registered of that type
     */
    inline fun <reified T : Any> get(): T {
        val type = typeOf<T>()
        val service: Any? = services[type]

        if (service is T) return service

        when (val lazyService = registry[type]?.let { it() }) {
            is T -> {
                services[type] = lazyService
                return lazyService
            }

            is Any -> throw InvalidRegistration(type)
            else -> {
                if (type == typeOf<Config>()) {
                    throw MissingConfig()
                } else {
                    throw MissingRegistration(type)
                }
            }
        }
    }
}
