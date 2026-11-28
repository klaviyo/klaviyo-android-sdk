package com.klaviyo.inbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Verifies the Mobile Inbox API is callable from Java without Kotlin-only types.
 */
public class MobileInboxJavaApiTest {

    @Test
    public void queryIsBuiltWithOverloadedConstructors() {
        assertEquals(20, new InboxQuery().getPageSize());
        assertEquals(5, new InboxQuery(5).getPageSize());
        InboxCursor cursor = new InboxCursor("token");
        assertEquals("token", cursor.getToken());
        assertEquals(cursor, new InboxQuery(10, cursor).getCursor());

        InboxQuery filtered = new InboxQuery(10, null, InboxReadFilter.UNREAD, InboxArchiveFilter.ARCHIVED);
        assertEquals(InboxReadFilter.UNREAD, filtered.getReadFilter());
        assertEquals(InboxArchiveFilter.ARCHIVED, filtered.getArchiveFilter());
        assertEquals(InboxArchiveFilter.ACTIVE, new InboxQuery().getArchiveFilter());
    }

    @Test
    public void filterIsBuiltWithFactoryAndConstant() {
        InboxFilter unreadActive = InboxFilter.where(InboxReadFilter.UNREAD, InboxArchiveFilter.ACTIVE);
        assertEquals(InboxReadFilter.UNREAD, unreadActive.getReadFilter());
        assertEquals(InboxArchiveFilter.ACTIVE, unreadActive.getArchiveFilter());
        assertEquals(unreadActive, InboxFilter.where(InboxReadFilter.UNREAD, InboxArchiveFilter.ACTIVE));

        assertEquals(InboxReadFilter.ALL, InboxFilter.ALL_MESSAGES.getReadFilter());
        assertEquals(InboxArchiveFilter.ALL, InboxFilter.ALL_MESSAGES.getArchiveFilter());
    }

    @Test
    public void callbacksAreLambdaCompatible() {
        InboxPageCallback page = result -> { };
        InboxCountCallback count = result -> { };
        InboxUpdateCallback update = result -> { };
        InboxClearCallback clear = result -> { };
        InboxChangeListener listener = () -> { };

        // Compiles only if the Java entry points accept these types
        Runnable unused = () -> {
            KlaviyoInbox.fetchPage(page);
            KlaviyoInbox.fetchPage(new InboxQuery(), page);
            KlaviyoInbox.fetchUnreadCount(count);
            KlaviyoInbox.updateMessageState("tm-1", InboxStateChange.MARK_READ, update);
            KlaviyoInbox.updateMessageState(InboxStateChange.MARK_READ, InboxFilter.ALL_MESSAGES, count);
            KlaviyoInbox.updateMessageState(
                InboxStateChange.ARCHIVE,
                java.util.Arrays.asList("tm-1", "tm-2"),
                count
            );
            KlaviyoInbox.clearAll(clear);
            KlaviyoInbox.addChangeListener(listener);
            KlaviyoInbox.removeChangeListener(listener);
        };
    }

    @Test
    public void clickTrackingIsCallableWithOverloads() {
        // Compiles only if the Java entry points expose the defaulted overloads
        Runnable unused = () -> {
            InboxMessage message = null;
            InboxAction action = null;
            KlaviyoInbox.trackMessageClick(message);
            KlaviyoInbox.trackMessageClick(message, action);
            KlaviyoInbox.trackMessageClick(message, action, java.util.Collections.singletonMap("screen", "inbox"));
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void resultsAreInspectableWithoutKotlin() {
        InboxResult<Integer> success = new InboxResult.Success<>(3);
        InboxResult<Integer> failure = new InboxResult.Failure(
                new InboxException.StorageFailure("failed", null)
        );

        assertEquals(3, (int) ((InboxResult.Success<Integer>) success).getValue());
        assertTrue(
                ((InboxResult.Failure) failure).getException() instanceof InboxException.StorageFailure
        );
    }
}
