package com.klaviyo.analytics;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.klaviyo.analytics.linking.DeepLinkHandler;
import com.klaviyo.analytics.model.Event;
import com.klaviyo.analytics.model.EventMetric;
import com.klaviyo.analytics.model.Profile;
import com.klaviyo.analytics.model.ProfileKey;
import com.klaviyo.fixtures.KlaviyoMock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify the Klaviyo SDK public API is accessible from Java.
 *
 * These tests demonstrate the current Java syntax required to call the SDK methods.
 * Since Klaviyo is a Kotlin object (singleton), Java callers must use Klaviyo.INSTANCE
 * to access methods. This is the "ugly but functional" syntax that works today.
 *
 * A future PR will add @JvmStatic annotations to enable the cleaner Klaviyo.methodName() syntax.
 */
public class KlaviyoJavaApiTest {

    private Context mockContext;
    private Intent mockIntent;
    private Uri mockUri;

    @Before
    public void setup() {
        KlaviyoMock.setup();
        mockContext = KlaviyoMock.getMockContext();
        mockIntent = KlaviyoMock.getMockIntent();
        mockUri = KlaviyoMock.getMockUri();
    }

    @After
    public void teardown() {
        KlaviyoMock.teardown();
    }

    /**
     * Test initialize method.
     * Current Java syntax: Klaviyo.INSTANCE.initialize(apiKey, context)
     * Desired syntax: Klaviyo.initialize(apiKey, context)
     */
    @Test
    public void testInitialize() {
        String apiKey = "test-api-key";

        Klaviyo result = Klaviyo.INSTANCE.initialize(apiKey, mockContext);

        KlaviyoMock.verifyInitializeCalled(apiKey);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test registerForLifecycleCallbacks method.
     */
    @Test
    public void testRegisterForLifecycleCallbacks() {
        Klaviyo result = Klaviyo.INSTANCE.registerForLifecycleCallbacks(mockContext);

        KlaviyoMock.verifyRegisterForLifecycleCallbacksCalled();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test setProfile method.
     */
    @Test
    public void testSetProfile() {
        // Profile requires at least one parameter from Java (no @JvmOverloads on constructor)
        Profile profile = new Profile(null, null, null, null);

        Klaviyo result = Klaviyo.INSTANCE.setProfile(profile);

        KlaviyoMock.verifySetProfileCalled();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test setEmail method.
     */
    @Test
    public void testSetEmail() {
        String email = "test@example.com";

        Klaviyo result = Klaviyo.INSTANCE.setEmail(email);

        KlaviyoMock.verifySetEmailCalled(email);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test getEmail method.
     */
    @Test
    public void testGetEmail() {
        String email = Klaviyo.INSTANCE.getEmail();

        assertNotNull("getEmail should return a value", email);
    }

    /**
     * Test setPhoneNumber method.
     */
    @Test
    public void testSetPhoneNumber() {
        String phone = "+15555555555";

        Klaviyo result = Klaviyo.INSTANCE.setPhoneNumber(phone);

        KlaviyoMock.verifySetPhoneNumberCalled(phone);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test getPhoneNumber method.
     */
    @Test
    public void testGetPhoneNumber() {
        String phone = Klaviyo.INSTANCE.getPhoneNumber();

        assertNotNull("getPhoneNumber should return a value", phone);
    }

    /**
     * Test setExternalId method.
     */
    @Test
    public void testSetExternalId() {
        String externalId = "ext-123";

        Klaviyo result = Klaviyo.INSTANCE.setExternalId(externalId);

        KlaviyoMock.verifySetExternalIdCalled(externalId);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test getExternalId method.
     */
    @Test
    public void testGetExternalId() {
        String externalId = Klaviyo.INSTANCE.getExternalId();

        assertNotNull("getExternalId should return a value", externalId);
    }

    /**
     * Test setPushToken method.
     */
    @Test
    public void testSetPushToken() {
        String token = "push-token-abc";

        Klaviyo result = Klaviyo.INSTANCE.setPushToken(token);

        KlaviyoMock.verifySetPushTokenCalled(token);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test getPushToken method.
     */
    @Test
    public void testGetPushToken() {
        String token = Klaviyo.INSTANCE.getPushToken();

        assertNotNull("getPushToken should return a value", token);
    }

    /**
     * Test setProfileAttribute method with a built-in ProfileKey.
     * Note: ProfileKey sealed class object members are accessed via .INSTANCE in Java.
     */
    @Test
    public void testSetProfileAttribute() {
        ProfileKey key = ProfileKey.FIRST_NAME.INSTANCE;
        String value = "John";

        Klaviyo result = Klaviyo.INSTANCE.setProfileAttribute(key, value);

        KlaviyoMock.verifySetProfileAttributeCalled(key, value);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test setProfileAttribute with a custom ProfileKey.
     */
    @Test
    public void testSetProfileAttributeCustomKey() {
        ProfileKey customKey = new ProfileKey.CUSTOM("custom_field");
        String value = "custom_value";

        Klaviyo result = Klaviyo.INSTANCE.setProfileAttribute(customKey, value);

        KlaviyoMock.verifySetProfileAttributeCalled(customKey, value);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test resetProfile method.
     */
    @Test
    public void testResetProfile() {
        Klaviyo result = Klaviyo.INSTANCE.resetProfile();

        KlaviyoMock.verifyResetProfileCalled();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test createEvent method with Event object.
     */
    @Test
    public void testCreateEventWithEvent() {
        Event event = new Event(EventMetric.VIEWED_PRODUCT.INSTANCE);

        Klaviyo result = Klaviyo.INSTANCE.createEvent(event);

        KlaviyoMock.verifyCreateEventCalled();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test createEvent method with EventMetric and value.
     */
    @Test
    public void testCreateEventWithMetric() {
        EventMetric metric = EventMetric.ADDED_TO_CART.INSTANCE;

        Klaviyo result = Klaviyo.INSTANCE.createEvent(metric, 19.99);

        KlaviyoMock.verifyCreateEventWithMetricCalled(metric);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test createEvent with custom EventMetric.
     */
    @Test
    public void testCreateEventWithCustomMetric() {
        EventMetric customMetric = new EventMetric.CUSTOM("Custom Event");

        Klaviyo result = Klaviyo.INSTANCE.createEvent(customMetric, null);

        KlaviyoMock.verifyCreateEventWithMetricCalled(customMetric);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test handlePush method.
     */
    @Test
    public void testHandlePush() {
        Klaviyo result = Klaviyo.INSTANCE.handlePush(mockIntent);

        KlaviyoMock.verifyHandlePushCalled();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test handleUniversalTrackingLink with String URL.
     */
    @Test
    public void testHandleUniversalTrackingLinkString() {
        String url = "https://trk.klviyomail.com/test";

        boolean result = Klaviyo.INSTANCE.handleUniversalTrackingLink(url);

        KlaviyoMock.verifyHandleUniversalTrackingLinkStringCalled(url);
        assertTrue("Should return true for tracking link", result);
    }

    /**
     * Test handleUniversalTrackingLink with Intent.
     */
    @Test
    public void testHandleUniversalTrackingLinkIntent() {
        boolean result = Klaviyo.INSTANCE.handleUniversalTrackingLink(mockIntent);

        KlaviyoMock.verifyHandleUniversalTrackingLinkIntentCalled();
        assertTrue("Should return true for tracking link", result);
    }

    /**
     * Test registerDeepLinkHandler method.
     * Verifies DeepLinkHandler can be implemented as a lambda in Java.
     */
    @Test
    public void testRegisterDeepLinkHandler() {
        DeepLinkHandler handler = uri -> {
            // Handle deep link
        };

        Klaviyo result = Klaviyo.INSTANCE.registerDeepLinkHandler(handler);

        KlaviyoMock.verifyRegisterDeepLinkHandlerCalled();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test unregisterDeepLinkHandler method.
     */
    @Test
    public void testUnregisterDeepLinkHandler() {
        Klaviyo result = Klaviyo.INSTANCE.unregisterDeepLinkHandler();

        KlaviyoMock.verifyUnregisterDeepLinkHandlerCalled();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test deprecated isKlaviyoIntent extension property.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testIsKlaviyoIntent() {
        boolean result = Klaviyo.INSTANCE.isKlaviyoIntent(mockIntent);

        assertTrue("Should return true", result);
    }

    /**
     * Test isKlaviyoNotificationIntent extension property.
     */
    @Test
    public void testIsKlaviyoNotificationIntent() {
        boolean result = Klaviyo.INSTANCE.isKlaviyoNotificationIntent(mockIntent);

        assertTrue("Should return true", result);
    }

    /**
     * Test isKlaviyoUniversalTrackingIntent extension property.
     */
    @Test
    public void testIsKlaviyoUniversalTrackingIntent() {
        boolean result = Klaviyo.INSTANCE.isKlaviyoUniversalTrackingIntent(mockIntent);

        assertTrue("Should return true", result);
    }

    /**
     * Test isKlaviyoUniversalTrackingUri extension property.
     */
    @Test
    public void testIsKlaviyoUniversalTrackingUri() {
        boolean result = Klaviyo.INSTANCE.isKlaviyoUniversalTrackingUri(mockUri);

        assertTrue("Should return true", result);
    }

    /**
     * Test method chaining works from Java.
     */
    @Test
    public void testMethodChaining() {
        // Verify fluent API works from Java
        Klaviyo.INSTANCE
            .setEmail("test@example.com")
            .setPhoneNumber("+15555555555")
            .setExternalId("ext-123");

        KlaviyoMock.verifySetEmailCalled("test@example.com");
        KlaviyoMock.verifySetPhoneNumberCalled("+15555555555");
        KlaviyoMock.verifySetExternalIdCalled("ext-123");
    }

    /**
     * Test Profile class is usable from Java.
     * Note: Profile doesn't have @JvmOverloads, so Java must pass all constructor params.
     */
    @Test
    public void testProfileClassUsableFromJava() {
        // Profile requires parameters from Java (no no-arg constructor exposed)
        Profile profile = new Profile(null, null, null, null);
        assertNotNull("Profile should be instantiable", profile);

        // Profile can be created with constructor parameters
        Profile profileWithParams = new Profile(
            "test@example.com",  // email
            "+15555555555",      // phone
            "ext-123",           // externalId
            null                 // properties map
        );
        assertNotNull("Profile with params should be instantiable", profileWithParams);
    }

    /**
     * Test Event class is usable from Java.
     */
    @Test
    public void testEventClassUsableFromJava() {
        // Event can be created with an EventMetric
        Event event = new Event(EventMetric.VIEWED_PRODUCT.INSTANCE);
        assertNotNull("Event should be instantiable", event);

        // Custom metric
        Event customEvent = new Event(new EventMetric.CUSTOM("Custom Event"));
        assertNotNull("Event with custom metric should be instantiable", customEvent);
    }
}
