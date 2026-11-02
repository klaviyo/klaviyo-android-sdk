package com.klaviyo.inbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Verifies the Mobile Inbox API is callable from Java without Kotlin-only types.
 */
public class MobileInboxJavaApiTest {

    @Test
    public void callbacksAreLambdaCompatible() {
        InboxCountCallback count = result -> { };

        // Compiles only if the Java entry point accepts the callback type
        Runnable unused = () -> KlaviyoInbox.fetchUnreadCount(count);
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
