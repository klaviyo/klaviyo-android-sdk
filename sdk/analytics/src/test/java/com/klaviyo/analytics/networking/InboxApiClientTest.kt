package com.klaviyo.analytics.networking

import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.HttpUtil
import com.klaviyo.analytics.networking.requests.mockInboxConnection
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import io.mockk.every
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class InboxApiClientTest : BaseTest() {

    private val token = "secret-token-value"
    private val observed = mutableListOf<ApiRequest>()
    private val observer: ApiObserver = { observed += it }

    private fun withConnectionMock(code: Int, body: String) =
        mockInboxConnection(mockConfig.baseUrl, code, body)

    @Before
    override fun setup() {
        super.setup()
        Dispatchers.setMain(dispatcher)
        mockDeviceProperties()
        every { mockNetworkMonitor.isNetworkConnected() } returns true
        KlaviyoApiClient.onApiRequest(observer = observer)
    }

    @After
    override fun cleanup() {
        KlaviyoApiClient.offApiRequest(observer)
        unmockkObject(HttpUtil)
        Dispatchers.resetMain()
        super.cleanup()
    }

    @Test
    fun `fetchInboxPage returns the raw response and sends the bearer token`() = runTest {
        val connection = withConnectionMock(200, """{"data":[]}""")

        val response = KlaviyoApiClient.fetchInboxPage(token, "p1", "c%2B=", 25)

        assertEquals(200, response.code)
        assertEquals("""{"data":[]}""", response.body)
        assertTrue(response.sent)
        verify { connection.setRequestProperty("Authorization", "Bearer $token") }
        verify {
            HttpUtil.openConnection(
                match {
                    it.query == "profile_id=p1&platform=android&page[size]=25&page[cursor]=c%2B="
                }
            )
        }
    }

    @Test
    fun `submitInboxStateChanges posts the body`() = runTest {
        withConnectionMock(200, "{}")

        val response = KlaviyoApiClient.submitInboxStateChanges(token, "{\"a\":1}")

        assertEquals(200, response.code)
        verify { HttpUtil.writeToConnection("{\"a\":1}", any()) }
    }

    @Test
    fun `Inbox calls are not broadcast, persisted or logged as errors on 4xx`() = runTest {
        withConnectionMock(401, "nope")
        val queueBefore = KlaviyoApiClient.getQueueSize()

        val fetched = KlaviyoApiClient.fetchInboxPage(token, "p1", null, 10)
        val submitted = KlaviyoApiClient.submitInboxStateChanges(token, "{}")

        assertEquals(401, fetched.code)
        assertEquals(401, submitted.code)
        assertTrue(observed.isEmpty())
        assertEquals(queueBefore, KlaviyoApiClient.getQueueSize())
        verify(exactly = 0) { spyDataStore.store(any(), any()) }
        verify(exactly = 0) { spyLog.warning(any(), any()) }
        verify(exactly = 0) { spyLog.error(any(), any()) }
    }
}
