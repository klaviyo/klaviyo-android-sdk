package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class KlaviyoStateTest : BaseTest() {

    private lateinit var state: KlaviyoState

    @Before
    override fun setup() {
        super.setup()
        state = KlaviyoState()
        Registry.register<ApiClient>(mockk<ApiClient>(relaxed = true))
    }

    @After
    override fun cleanup() {
        state.reset()
        super.cleanup()
    }

    @Test
    fun `State observers concurrency test`() = runTest {
        val observer: StateChangeObserver = { _ -> Thread.sleep(6) }

        state.onStateChange(observer)

        val job = launch(Dispatchers.IO) {
            state.reset()
        }

        val job2 = launch(Dispatchers.Default) {
            withContext(Dispatchers.IO) {
                Thread.sleep(8)
            }
            state.offStateChange(observer)
        }

        job.start()
        job2.start()
    }

    @Test
    fun `Profile events observer concurrency test`() = runTest {
        val observer: ProfileEventObserver = { _ -> Thread.sleep(6) }

        state.onProfileEvent(observer)

        val job = launch(Dispatchers.IO) {
            state.createEvent(mockk(relaxed = true), mockk())
        }

        val job2 = launch(Dispatchers.Default) {
            withContext(Dispatchers.IO) {
                Thread.sleep(8)
            }
            state.offProfileEvent(observer)
        }

        job.start()
        job2.start()
    }

    @Test
    fun `Observer can detach itself during callback`() = runTest {
        var observer: StateChangeObserver = { _ -> }
        var didRun = false

        observer = { _ ->
            state.offStateChange(observer)
            didRun = true
        }

        state.onStateChange(observer)

        state.reset()
        assert(didRun) { "Observer did not run as expected" }

        didRun = false
        state.reset()
        assert(!didRun) { "Observer should not run the second time" }
    }

    @Test
    fun `UserInfo is convertible to Profile`() {
        state.externalId = EXTERNAL_ID
        state.email = EMAIL
        state.phoneNumber = PHONE

        val profile = state.getAsProfile()

        assert(profile.externalId == EXTERNAL_ID)
        assert(profile.email == EMAIL)
        assert(profile.phoneNumber == PHONE)
        assert(profile.anonymousId == state.anonymousId)
        assert(profile.toMap().count() == 4) // shouldn't contain any extras

        assertEquals(EXTERNAL_ID, state.externalId)
        assertEquals(EMAIL, state.email)
        assertEquals(PHONE, state.phoneNumber)
    }

    @Test
    fun `Create and store a new UUID if one does not exists in data store`() {
        val anonId = state.anonymousId
        val fetched = spyDataStore.fetch(ProfileKey.ANONYMOUS_ID.name)
        assertEquals(anonId, fetched)
    }

    @Test
    fun `Do not create new UUID if one exists in data store`() {
        spyDataStore.store(ProfileKey.ANONYMOUS_ID.name, ANON_ID)
        assertEquals(ANON_ID, state.anonymousId)
    }

    @Test
    fun `Only read properties from data store once`() {
        spyDataStore.store(ProfileKey.ANONYMOUS_ID.name, ANON_ID)
        spyDataStore.store(ProfileKey.EXTERNAL_ID.name, EXTERNAL_ID)
        spyDataStore.store(ProfileKey.EMAIL.name, EMAIL)
        spyDataStore.store(ProfileKey.PHONE_NUMBER.name, PHONE)

        state.anonymousId
        assertEquals(ANON_ID, state.anonymousId)
        verify(exactly = 1) { spyDataStore.fetch(ProfileKey.ANONYMOUS_ID.name) }

        state.externalId
        assertEquals(EXTERNAL_ID, state.externalId)
        verify(exactly = 1) { spyDataStore.fetch(ProfileKey.EXTERNAL_ID.name) }

        state.email
        assertEquals(EMAIL, state.email)
        verify(exactly = 1) { spyDataStore.fetch(ProfileKey.EMAIL.name) }

        state.phoneNumber
        assertEquals(PHONE, state.phoneNumber)
        verify(exactly = 1) { spyDataStore.fetch(ProfileKey.PHONE_NUMBER.name) }
    }

    @Test
    fun `Anonymous ID lifecycle`() {
        // Should be null after a reset...
        val initialAnonId = spyDataStore.fetch(ProfileKey.ANONYMOUS_ID.name)
        assertNull(initialAnonId)

        // Start tracking a new anon ID and it should be persisted
        val firstAnonId = state.anonymousId
        assertEquals(firstAnonId, spyDataStore.fetch(ProfileKey.ANONYMOUS_ID.name))

        // Reset again should nullify in data store
        state.reset()
        assertNull(spyDataStore.fetch(ProfileKey.ANONYMOUS_ID.name))

        // Start tracking again should generate another new anon ID
        val newAnonId = state.anonymousId
        assertNotEquals(firstAnonId, newAnonId)
        assertEquals(newAnonId, spyDataStore.fetch(ProfileKey.ANONYMOUS_ID.name))
    }

    @Test
    fun `Broadcasts change of property with key and old value`() {
        spyDataStore.store(ProfileKey.EXTERNAL_ID.name, EXTERNAL_ID)
        spyDataStore.store(ProfileKey.EMAIL.name, EMAIL)
        spyDataStore.store(ProfileKey.PHONE_NUMBER.name, PHONE)

        var broadcastChange: StateChange.ProfileIdentifier? = null

        state.onStateChange { change ->
            broadcastChange = change as? StateChange.ProfileIdentifier
        }

        state.externalId = "new_external_id"
        assertEquals(ProfileKey.EXTERNAL_ID, broadcastChange?.key)
        assertEquals(EXTERNAL_ID, broadcastChange?.oldValue)

        state.email = "new@email.com"
        assertEquals(ProfileKey.EMAIL, broadcastChange?.key)
        assertEquals(EMAIL, broadcastChange?.oldValue)

        state.phoneNumber = "new_phone"
        assertEquals(ProfileKey.PHONE_NUMBER, broadcastChange?.key)
        assertEquals(PHONE, broadcastChange?.oldValue)
    }

    @Test
    fun `Broadcasts on set attributes`() {
        var broadcastChange: StateChange.ProfileAttributes? = null
        val customKey = ProfileKey.CUSTOM("color")

        state.onStateChange { change ->
            broadcastChange = change as? StateChange.ProfileAttributes
        }

        state.setAttribute(ProfileKey.FIRST_NAME, "Kermit")
        state.setAttribute(customKey, "Green")
        state.setAttribute(ProfileKey.LAST_NAME, "Frog")

        assertEquals("Kermit", broadcastChange?.oldValue?.get(ProfileKey.FIRST_NAME))
        assertEquals("Green", broadcastChange?.oldValue?.get(customKey))
        assertNull("Green", broadcastChange?.oldValue?.get(ProfileKey.LAST_NAME))
    }

    @Test
    fun `Set attributes does not set on non-string profile info`() {
        var broadcastChange: StateChange.ProfileAttributes? = null

        state.onStateChange { change ->
            broadcastChange = change as? StateChange.ProfileAttributes
        }

        // expecting a string but sending an int, should not be set
        state.setAttribute(ProfileKey.EMAIL, 29864)
        state.setAttribute(ProfileKey.LAST_NAME, "Frog")

        assertEquals(null, broadcastChange?.oldValue?.get(ProfileKey.EMAIL))
    }

    @Test
    fun `Broadcasts on reset attributes`() {
        var broadcastChange: StateChange? = null

        state.setAttribute(ProfileKey.FIRST_NAME, "Kermit")
        state.onStateChange { change ->
            broadcastChange = change
        }

        state.resetAttributes()

        assert(broadcastChange is StateChange.ProfileAttributes)
    }

    @Test
    fun `Broadcasts on reset profile`() {
        state.externalId = EXTERNAL_ID
        state.email = EMAIL
        state.phoneNumber = PHONE
        state.setAttribute(ProfileKey.FIRST_NAME, "Kermit")
        state.setAttribute(ProfileKey.LAST_NAME, "Frog")

        var broadcastChange: StateChange.ProfileReset? = null

        state.onStateChange { change ->
            broadcastChange = change as? StateChange.ProfileReset
        }

        state.reset()

        val broadcastProfile = broadcastChange?.oldValue ?: Profile()

        assertEquals(EXTERNAL_ID, broadcastProfile.externalId)
        assertEquals(EMAIL, broadcastProfile.email)
        assertEquals(PHONE, broadcastProfile.phoneNumber)
        assertEquals("Kermit", broadcastProfile[ProfileKey.FIRST_NAME])
        assertEquals("Frog", broadcastProfile[ProfileKey.LAST_NAME])
    }

    @Test
    fun `Resetting profile email and phone number values`() {
        state.email = EMAIL
        state.phoneNumber = PHONE

        state.resetEmail()
        state.resetPhoneNumber()

        assertEquals(state.email, null)
        assertEquals(state.phoneNumber, null)
    }
}
