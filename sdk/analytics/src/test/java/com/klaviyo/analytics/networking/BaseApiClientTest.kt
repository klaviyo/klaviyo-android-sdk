package com.klaviyo.analytics.networking

import com.klaviyo.core.Registry
import com.klaviyo.core.networking.BaseApiClient
import com.klaviyo.fixtures.BaseTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class BaseApiClientTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
        // Register KlaviyoApiClient as both BaseApiClient and ApiClient
        // This mimics what happens in Klaviyo.registerForLifecycleCallbacks without the full initialization
        Registry.register<BaseApiClient>(KlaviyoApiClient)
        Registry.register<ApiClient>(KlaviyoApiClient)
    }

    @After
    override fun cleanup() {
        Registry.unregister<BaseApiClient>()
        Registry.unregister<ApiClient>()
        super.cleanup()
    }

    @Test
    fun `KlaviyoApiClient is registered as BaseApiClient`() {
        // Act - get the BaseApiClient from registry
        val baseApiClient = Registry.get<BaseApiClient>()

        // Assert
        Assert.assertNotNull("BaseApiClient should be registered", baseApiClient)
        Assert.assertTrue(
            "BaseApiClient should be KlaviyoApiClient instance",
            baseApiClient is KlaviyoApiClient
        )
    }

    @Test
    fun `KlaviyoApiClient is registered as ApiClient`() {
        // Act - get the ApiClient from registry
        val apiClient = Registry.get<ApiClient>()

        // Assert
        Assert.assertNotNull("ApiClient should be registered", apiClient)
        Assert.assertTrue(
            "ApiClient should be KlaviyoApiClient instance",
            apiClient is KlaviyoApiClient
        )
    }

    @Test
    fun `Both BaseApiClient and ApiClient resolve to same instance`() {
        // Act - get both interfaces
        val baseApiClient = Registry.get<BaseApiClient>()
        val apiClient = Registry.get<ApiClient>()

        // Assert - they should be the exact same instance
        Assert.assertSame(
            "BaseApiClient and ApiClient should resolve to the same KlaviyoApiClient instance",
            baseApiClient,
            apiClient
        )
    }

    @Test
    fun `BaseApiClient interface methods are available`() {
        // Act - get BaseApiClient
        val baseApiClient = Registry.get<BaseApiClient>()

        // Assert - verify we can reference the methods (won't actually call them)
        Assert.assertNotNull(baseApiClient::startService)
        Assert.assertNotNull(baseApiClient::persistQueue)
        Assert.assertNotNull(baseApiClient::restoreQueue)
        Assert.assertNotNull(baseApiClient::flushQueue)
    }
}
