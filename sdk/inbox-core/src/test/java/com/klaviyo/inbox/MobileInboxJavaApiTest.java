package com.klaviyo.inbox;

import static org.junit.Assert.assertEquals;

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
                new InboxException(InboxException.Reason.STORAGE_FAILURE, "failed", null)
        );

        assertEquals(3, (int) ((InboxResult.Success<Integer>) success).getValue());
        assertEquals(
                InboxException.Reason.STORAGE_FAILURE,
                ((InboxResult.Failure) failure).getException().getReason()
        );
    }
}
