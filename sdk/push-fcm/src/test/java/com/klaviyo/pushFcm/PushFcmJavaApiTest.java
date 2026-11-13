package com.klaviyo.pushFcm;

import com.google.firebase.messaging.RemoteMessage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify the push-fcm notification observer API is accessible from Java.
 */
public class PushFcmJavaApiTest {

    @Test
    public void testObserverLambda() {
        PushNotificationDispatcher dispatcher = PushFcmJavaApiFixtures.dispatcher();
        RemoteMessage message = PushFcmJavaApiFixtures.message(Collections.emptyMap());
        List<RemoteMessage> received = new ArrayList<>();
        KlaviyoNotificationObserver observer = received::add;

        dispatcher.onKlaviyoNotification(observer);
        PushFcmJavaApiFixtures.dispatch(dispatcher, message);

        assertEquals(1, received.size());
        assertSame(message, received.get(0));
    }

    @Test
    public void testOffKlaviyoNotification() {
        PushNotificationDispatcher dispatcher = PushFcmJavaApiFixtures.dispatcher();
        List<RemoteMessage> received = new ArrayList<>();
        KlaviyoNotificationObserver observer = received::add;

        dispatcher.onKlaviyoNotification(observer);
        dispatcher.offKlaviyoNotification(observer);
        PushFcmJavaApiFixtures.dispatch(dispatcher, PushFcmJavaApiFixtures.message(Collections.emptyMap()));

        assertTrue(received.isEmpty());
    }

    @Test
    public void testTapDestination() {
        RemoteMessage message = PushFcmJavaApiFixtures.message(Collections.emptyMap());

        KlaviyoRemoteMessage.TapDestination destination =
            KlaviyoRemoteMessage.INSTANCE.getTapDestination(message);

        assertSame(KlaviyoRemoteMessage.TapDestination.OpenApp.INSTANCE, destination);
    }
}
