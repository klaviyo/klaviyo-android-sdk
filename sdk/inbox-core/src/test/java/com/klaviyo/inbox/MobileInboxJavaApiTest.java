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
        assertEquals(cursor, new InboxQuery(10, cursor).getCursor());
    }

    @Test
    public void callbacksAreLambdaCompatible() {
        InboxPageCallback page = result -> { };
        InboxCountCallback count = result -> { };

        // Compiles only if the Java entry points accept these types
        Runnable unused = () -> {
            KlaviyoInbox.fetchPage(page);
            KlaviyoInbox.fetchPage(new InboxQuery(), page);
            KlaviyoInbox.fetchUnreadCount(count);
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
