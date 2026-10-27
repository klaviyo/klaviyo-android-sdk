package com.klaviyo.inbox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Verifies the Mobile Inbox API is callable from Java without Kotlin-only types.
 */
public class MobileInboxJavaApiTest {

    @Test
    public void queryIsBuiltWithOverloadedConstructors() {
        assertEquals(20, new InboxQuery().getPageSize());
        assertEquals(5, new InboxQuery(5).getPageSize());

        InboxQuery query = new InboxQuery(
                10,
                null,
                InboxReadFilter.UNREAD,
                InboxArchiveFilter.ALL,
                InboxSortOrder.OLDEST_FIRST
        );
        assertEquals(InboxReadFilter.UNREAD, query.getReadFilter());
    }

    @Test
    public void rawValueTypesAreStaticFields() {
        assertEquals("deep_link", InboxDestinationType.DEEP_LINK.getRawValue());
        assertEquals("captured", InboxMessageSource.CAPTURED.getRawValue());
    }

    @Test
    public void callbacksAreLambdaCompatible() {
        InboxPageCallback page = result -> { };
        InboxCountCallback count = result -> { };
        InboxResultCallback state = result -> { };
        InboxChangeListener listener = () -> { };

        // Compiles only if the Java entry points accept these types
        Runnable unused = () -> {
            KlaviyoInbox.fetchPage(page);
            KlaviyoInbox.fetchPage(new InboxQuery(), page);
            KlaviyoInbox.fetchUnreadCount(count);
            KlaviyoInbox.updateMessageState("id", InboxStateChange.MARK_READ, state);
            KlaviyoInbox.addChangeListener(listener);
            KlaviyoInbox.removeChangeListener(listener);
        };
    }

    @Test
    public void resultsAreInspectableWithoutKotlin() {
        InboxResult<Integer> success = new InboxResult.Success<>(3);
        InboxResult<Integer> failure = new InboxResult.Failure(
                new InboxException(InboxException.Reason.STORAGE_FAILURE, "failed", null)
        );

        assertEquals(3, (int) ((InboxResult.Success<Integer>) success).getValue());
        assertEquals(
                InboxException.Reason.STORAGE_FAILURE,
                ((InboxResult.Failure) failure).getException().getReason()
        );
    }
}
