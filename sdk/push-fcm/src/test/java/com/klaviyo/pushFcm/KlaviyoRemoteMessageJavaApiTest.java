package com.klaviyo.pushFcm;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.firebase.messaging.RemoteMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify the KlaviyoRemoteMessage API is accessible from Java.
 *
 * These tests demonstrate the current Java syntax required to call the extension properties
 * and functions defined in the KlaviyoRemoteMessage object. Since these are extension properties
 * on RemoteMessage defined inside a Kotlin object, Java callers must use
 * KlaviyoRemoteMessage.INSTANCE.getPropertyName(remoteMessage) syntax.
 *
 * For example:
 * - Kotlin: remoteMessage.isKlaviyoMessage
 * - Java: KlaviyoRemoteMessage.INSTANCE.isKlaviyoMessage(remoteMessage)
 */
public class KlaviyoRemoteMessageJavaApiTest {

    private RemoteMessage mockRemoteMessage;
    private Context mockContext;
    private Intent mockIntent;

    @Before
    public void setup() {
        KlaviyoRemoteMessageMock.setup();
        mockRemoteMessage = KlaviyoRemoteMessageMock.getMockRemoteMessage();
        mockContext = KlaviyoRemoteMessageMock.getMockContext();
        mockIntent = KlaviyoRemoteMessageMock.getMockIntent();
    }

    @After
    public void teardown() {
        KlaviyoRemoteMessageMock.teardown();
    }

    /**
     * Test isKlaviyoMessage extension property.
     * Current Java syntax: KlaviyoRemoteMessage.INSTANCE.isKlaviyoMessage(message)
     * Kotlin syntax: message.isKlaviyoMessage
     */
    @Test
    public void testIsKlaviyoMessage() {
        boolean result = KlaviyoRemoteMessage.INSTANCE.isKlaviyoMessage(mockRemoteMessage);

        assertTrue("Should return true for Klaviyo message", result);
        KlaviyoRemoteMessageMock.verifyIsKlaviyoMessageCalled();
    }

    /**
     * Test isKlaviyoNotification extension property.
     */
    @Test
    public void testIsKlaviyoNotification() {
        boolean result = KlaviyoRemoteMessage.INSTANCE.isKlaviyoNotification(mockRemoteMessage);

        assertTrue("Should return true for Klaviyo notification", result);
    }

    /**
     * Test title extension property.
     */
    @Test
    public void testGetTitle() {
        String title = KlaviyoRemoteMessage.INSTANCE.getTitle(mockRemoteMessage);

        assertEquals("Test Title", title);
        KlaviyoRemoteMessageMock.verifyGetTitleCalled();
    }

    /**
     * Test body extension property.
     */
    @Test
    public void testGetBody() {
        String body = KlaviyoRemoteMessage.INSTANCE.getBody(mockRemoteMessage);

        assertEquals("Test Body", body);
    }

    /**
     * Test channel_id extension property.
     */
    @Test
    public void testGetChannelId() {
        String channelId = KlaviyoRemoteMessage.INSTANCE.getChannel_id(mockRemoteMessage);

        assertEquals("test-channel", channelId);
    }

    /**
     * Test channel_name extension property.
     */
    @Test
    public void testGetChannelName() {
        String channelName = KlaviyoRemoteMessage.INSTANCE.getChannel_name(mockRemoteMessage);

        assertEquals("Test Channel", channelName);
    }

    /**
     * Test channel_description extension property.
     */
    @Test
    public void testGetChannelDescription() {
        String channelDescription = KlaviyoRemoteMessage.INSTANCE.getChannel_description(mockRemoteMessage);

        assertEquals("Test Description", channelDescription);
    }

    /**
     * Test channel_importance extension property.
     */
    @Test
    public void testGetChannelImportance() {
        int importance = KlaviyoRemoteMessage.INSTANCE.getChannel_importance(mockRemoteMessage);

        assertEquals(3, importance);
    }

    /**
     * Test notificationPriority extension property.
     */
    @Test
    public void testGetNotificationPriority() {
        int priority = KlaviyoRemoteMessage.INSTANCE.getNotificationPriority(mockRemoteMessage);

        assertEquals(0, priority);
    }

    /**
     * Test notificationTag extension property.
     */
    @Test
    public void testGetNotificationTag() {
        String tag = KlaviyoRemoteMessage.INSTANCE.getNotificationTag(mockRemoteMessage);

        assertEquals("test-tag", tag);
    }

    /**
     * Test notificationCount extension property.
     */
    @Test
    public void testGetNotificationCount() {
        int count = KlaviyoRemoteMessage.INSTANCE.getNotificationCount(mockRemoteMessage);

        assertEquals(1, count);
    }

    /**
     * Test deepLink extension property.
     */
    @Test
    public void testGetDeepLink() {
        Uri deepLink = KlaviyoRemoteMessage.INSTANCE.getDeepLink(mockRemoteMessage);

        assertNotNull("DeepLink should not be null", deepLink);
    }

    /**
     * Test sound extension property.
     */
    @Test
    public void testGetSound() {
        Uri sound = KlaviyoRemoteMessage.INSTANCE.getSound(mockRemoteMessage);

        assertNotNull("Sound should not be null", sound);
    }

    /**
     * Test hasKlaviyoKeyValuePairs extension property.
     */
    @Test
    public void testHasKlaviyoKeyValuePairs() {
        boolean hasKvp = KlaviyoRemoteMessage.INSTANCE.getHasKlaviyoKeyValuePairs(mockRemoteMessage);

        assertTrue("Should have key-value pairs", hasKvp);
    }

    /**
     * Test keyValuePairs extension property.
     */
    @Test
    public void testGetKeyValuePairs() {
        Map<String, String> kvp = KlaviyoRemoteMessage.INSTANCE.getKeyValuePairs(mockRemoteMessage);

        assertNotNull("Key-value pairs should not be null", kvp);
        assertEquals("value", kvp.get("key"));
    }

    /**
     * Test getSmallIcon function.
     * This is a function rather than property, so Java calls it as: getSmallIcon(message, context)
     */
    @Test
    public void testGetSmallIcon() {
        int iconRes = KlaviyoRemoteMessage.INSTANCE.getSmallIcon(mockRemoteMessage, mockContext);

        assertEquals(android.R.drawable.sym_def_app_icon, iconRes);
        KlaviyoRemoteMessageMock.verifyGetSmallIconCalled();
    }

    /**
     * Test getColor function.
     */
    @Test
    public void testGetColor() {
        Integer color = KlaviyoRemoteMessage.INSTANCE.getColor(mockRemoteMessage, mockContext);

        assertNotNull("Color should not be null", color);
        assertEquals(Integer.valueOf(0xFF0000), color);
    }

    /**
     * Test appendKlaviyoExtras Intent extension function.
     * Current Java syntax: KlaviyoRemoteMessage.INSTANCE.appendKlaviyoExtras(intent, message)
     * Kotlin syntax: intent.appendKlaviyoExtras(message)
     */
    @Test
    public void testAppendKlaviyoExtras() {
        Intent result = KlaviyoRemoteMessage.INSTANCE.appendKlaviyoExtras(mockIntent, mockRemoteMessage);

        assertNotNull("Should return Intent for chaining", result);
    }
}
