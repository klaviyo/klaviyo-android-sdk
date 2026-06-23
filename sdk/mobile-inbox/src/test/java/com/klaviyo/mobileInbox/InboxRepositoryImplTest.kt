package com.klaviyo.mobileInbox

import com.klaviyo.fixtures.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class InboxRepositoryImplTest : BaseTest() {

    private val mockDao = mockk<InboxMessageDao>(relaxed = true)
    private val mockApiService = mockk<InboxApiService>()
    private var profileParams = InboxProfileParams()

    private lateinit var repository: InboxRepositoryImpl

    @Before
    override fun setup() {
        super.setup()
        repository = InboxRepositoryImpl(mockDao, mockApiService) { profileParams }
        coEvery { mockDao.observeMessages() } returns flowOf(emptyList())
        coEvery { mockDao.observeUnreadCount() } returns flowOf(0)
    }

    @Test
    fun `sync skips fetch when no profile identifier`() = runTest {
        profileParams = InboxProfileParams()
        repository.sync()
        coVerify(exactly = 0) { mockApiService.fetchMessages(any()) }
    }

    @Test
    fun `sync fetches when profile has email`() = runTest {
        profileParams = InboxProfileParams(email = "test@klaviyo.com")
        coEvery { mockApiService.fetchMessages(any()) } returns emptyList()
        repository.sync()
        coVerify(exactly = 1) { mockApiService.fetchMessages(profileParams) }
    }

    @Test
    fun `sync preserves READ status when remote returns same message as UNREAD`() = runTest {
        profileParams = InboxProfileParams(email = "test@klaviyo.com")
        val existingEntity = InboxMessageEntity(
            id = "msg-1",
            timestamp = 1000L,
            title = "Old Title",
            body = "Old Body",
            status = InboxStatus.READ,
            source = InboxSource.PUSH
        )
        val remoteMessage = InboxMessage(
            id = "msg-1",
            timestamp = 2000L,
            title = "New Title",
            body = "New Body",
            status = InboxStatus.UNREAD,
            source = InboxSource.REMOTE
        )

        coEvery { mockApiService.fetchMessages(any()) } returns listOf(remoteMessage)
        coEvery { mockDao.getMessagesByIds(listOf("msg-1")) } returns listOf(existingEntity)

        repository.sync()

        coVerify {
            mockDao.upsertMessages(
                withArg { entities ->
                    val upserted = entities.first()
                    assertEquals(InboxStatus.READ, upserted.status)
                    assertEquals("New Title", upserted.title)
                }
            )
        }
    }

    @Test
    fun `sync preserves HIDDEN status when remote returns same message`() = runTest {
        profileParams = InboxProfileParams(anonymousId = "anon-123")
        val existingEntity = InboxMessageEntity(
            id = "msg-2",
            timestamp = 1000L,
            title = "Title",
            body = "Body",
            status = InboxStatus.HIDDEN,
            source = InboxSource.PUSH
        )
        val remoteMessage = InboxMessage(
            id = "msg-2",
            timestamp = 1000L,
            title = "Title",
            body = "Body",
            status = InboxStatus.UNREAD,
            source = InboxSource.REMOTE
        )

        coEvery { mockApiService.fetchMessages(any()) } returns listOf(remoteMessage)
        coEvery { mockDao.getMessagesByIds(listOf("msg-2")) } returns listOf(existingEntity)

        repository.sync()

        coVerify {
            mockDao.upsertMessages(
                withArg { entities ->
                    assertEquals(InboxStatus.HIDDEN, entities.first().status)
                }
            )
        }
    }

    @Test
    fun `sync inserts new remote message with UNREAD when not in local db`() = runTest {
        profileParams = InboxProfileParams(phoneNumber = "+12223334444")
        val remoteMessage = InboxMessage(
            id = "new-msg",
            timestamp = 5000L,
            title = "New",
            body = "Message",
            status = InboxStatus.UNREAD,
            source = InboxSource.REMOTE
        )

        coEvery { mockApiService.fetchMessages(any()) } returns listOf(remoteMessage)
        coEvery { mockDao.getMessagesByIds(listOf("new-msg")) } returns emptyList()

        repository.sync()

        coVerify {
            mockDao.upsertMessages(
                withArg { entities ->
                    assertEquals(InboxStatus.UNREAD, entities.first().status)
                    assertEquals("new-msg", entities.first().id)
                }
            )
        }
    }

    @Test
    fun `upsertFromPush delegates to dao`() = runTest {
        val entity = InboxMessageEntity(
            id = "push-1",
            timestamp = 1000L,
            title = "Push",
            body = "Body",
            source = InboxSource.PUSH
        )
        repository.upsertFromPush(entity)
        coVerify { mockDao.upsertMessages(listOf(entity)) }
    }

    @Test
    fun `markRead updates status to READ`() = runTest {
        repository.markRead("msg-1")
        coVerify { mockDao.updateStatus("msg-1", InboxStatus.READ) }
    }

    @Test
    fun `markHidden updates status to HIDDEN`() = runTest {
        repository.markHidden("msg-1")
        coVerify { mockDao.updateStatus("msg-1", InboxStatus.HIDDEN) }
    }
}
