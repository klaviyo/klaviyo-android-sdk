package com.klaviyo.analytics;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.klaviyo.analytics.linking.DeepLinkHandler;
import com.klaviyo.analytics.model.Event;
import com.klaviyo.analytics.model.EventKey;
import com.klaviyo.analytics.model.EventMetric;
import com.klaviyo.analytics.model.Profile;
import com.klaviyo.analytics.model.ProfileKey;
import com.klaviyo.fixtures.KlaviyoMock;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

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
     * Test Profile constructor with properties map from Java.
     * Demonstrates the awkward syntax required without @JvmOverloads.
     */
    @Test
    public void testProfileConstructorWithPropertiesMap() {
        // Create a properties map with ProfileKey
        Map<ProfileKey, Serializable> properties = new HashMap<>();
        properties.put(ProfileKey.FIRST_NAME.INSTANCE, "John");
        properties.put(ProfileKey.LAST_NAME.INSTANCE, "Doe");
        properties.put(new ProfileKey.CUSTOM("loyalty_points"), 100);

        // Must pass all 4 params even when we just want properties
        Profile profile = new Profile(null, null, null, properties);

        assertNotNull("Profile with properties should be instantiable", profile);
    }

    /**
     * Test Profile setters work from Java.
     *
     * IMPORTANT: Profile has ambiguous setter methods (setEmail, setPhoneNumber, setExternalId)
     * due to Kotlin generating both property setters (void return) and fluent setters (Profile return).
     * Java cannot resolve which method to call, so these setters CANNOT be used from Java.
     *
     * Workarounds:
     * 1. Use the constructor with parameters: new Profile("ext-123", "email@test.com", "+1555", null)
     * 2. Use setProperty with ProfileKey: profile.setProperty(ProfileKey.EMAIL, "email@test.com")
     *
     * This is a known Java interop issue that could be fixed by renaming the fluent methods
     * (e.g., withEmail instead of setEmail) in a future SDK version.
     */
    @Test
    public void testProfileSettersAmbiguity() {
        // These would fail to compile due to ambiguous method resolution:
        // profile.setEmail("test@example.com");     // DOES NOT COMPILE
        // profile.setPhoneNumber("+15555555555");   // DOES NOT COMPILE
        // profile.setExternalId("ext-123");         // DOES NOT COMPILE

        // Workaround 1: Use the constructor
        Profile profile1 = new Profile("ext-123", "test@example.com", "+15555555555", null);
        assertEquals("test@example.com", profile1.getEmail());

        // Workaround 2: Use setProperty with ProfileKey (no ambiguity)
        // Note: identifiers like email/phone/externalId use internal ProfileKeys,
        // so for those, use the constructor. For other properties:
        Profile profile2 = new Profile(null, null, null, null);
        profile2.setProperty(ProfileKey.FIRST_NAME.INSTANCE, "John")
                .setProperty("custom_field", "custom_value");

        assertNotNull("Profile with setProperty should work", profile2);
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

    /**
     * Test Event constructor with properties map from Java.
     */
    @Test
    public void testEventConstructorWithPropertiesMap() {
        Map<EventKey, Serializable> properties = new HashMap<>();
        properties.put(EventKey.VALUE.INSTANCE, 19.99);
        properties.put(new EventKey.CUSTOM("product_id"), "SKU-123");

        // Event with metric and properties
        Event event = new Event(EventMetric.ADDED_TO_CART.INSTANCE, properties);

        assertNotNull("Event with properties should be instantiable", event);
    }

    /**
     * Test Event with string metric name from Java.
     */
    @Test
    public void testEventWithStringMetric() {
        // Event can be created with a string metric name
        Event event = new Event("My Custom Event");
        assertNotNull("Event with string metric should be instantiable", event);

        // With properties
        Map<EventKey, Serializable> properties = new HashMap<>();
        properties.put(new EventKey.CUSTOM("item"), "Widget");
        Event eventWithProps = new Event("Purchase", properties);
        assertNotNull("Event with string metric and properties should be instantiable", eventWithProps);
    }

    /**
     * Test Event setters work from Java.
     *
     * IMPORTANT: Similar to Profile, Event has ambiguous setter methods (setValue, setUniqueId)
     * due to Kotlin generating both property setters (void) and fluent setters (Event).
     * Java cannot resolve which method to call.
     *
     * Workarounds:
     * 1. Use setProperty with EventKey: event.setProperty(EventKey.VALUE.INSTANCE, 99.99)
     * 2. Use the constructor with properties map
     *
     * This is a known Java interop issue.
     */
    @Test
    public void testEventSettersAmbiguity() {
        // These would fail to compile due to ambiguous method resolution:
        // event.setValue(99.99);          // DOES NOT COMPILE
        // event.setUniqueId("order-123"); // DOES NOT COMPILE

        Event event = new Event(EventMetric.STARTED_CHECKOUT.INSTANCE);

        // Workaround: Use setProperty with EventKey (no ambiguity)
        event.setProperty(EventKey.VALUE.INSTANCE, 99.99)
             .setProperty(EventKey.EVENT_ID.INSTANCE, "order-123")
             .setProperty(new EventKey.CUSTOM("items_count"), 3);

        assertNotNull("Event with setProperty should work", event);
        // getValue() still works for reading
        assertEquals(Double.valueOf(99.99), event.getValue());
    }

    // ==========================================
    // Sealed Class .INSTANCE Access Tests
    // ==========================================

    /**
     * Test ProfileKey sealed class object members require .INSTANCE from Java.
     * This is the expected behavior for Kotlin sealed class objects in Java.
     */
    @Test
    public void testProfileKeyObjectMembersRequireInstance() {
        // Built-in ProfileKey objects - Java must use .INSTANCE
        ProfileKey firstName = ProfileKey.FIRST_NAME.INSTANCE;
        ProfileKey lastName = ProfileKey.LAST_NAME.INSTANCE;
        ProfileKey organization = ProfileKey.ORGANIZATION.INSTANCE;
        ProfileKey title = ProfileKey.TITLE.INSTANCE;
        ProfileKey image = ProfileKey.IMAGE.INSTANCE;
        ProfileKey address1 = ProfileKey.ADDRESS1.INSTANCE;
        ProfileKey address2 = ProfileKey.ADDRESS2.INSTANCE;
        ProfileKey city = ProfileKey.CITY.INSTANCE;
        ProfileKey country = ProfileKey.COUNTRY.INSTANCE;
        ProfileKey latitude = ProfileKey.LATITUDE.INSTANCE;
        ProfileKey longitude = ProfileKey.LONGITUDE.INSTANCE;
        ProfileKey region = ProfileKey.REGION.INSTANCE;
        ProfileKey zip = ProfileKey.ZIP.INSTANCE;
        ProfileKey timezone = ProfileKey.TIMEZONE.INSTANCE;

        assertNotNull("ProfileKey.FIRST_NAME.INSTANCE should be accessible", firstName);
        assertNotNull("ProfileKey.LAST_NAME.INSTANCE should be accessible", lastName);
        assertNotNull("ProfileKey.ORGANIZATION.INSTANCE should be accessible", organization);
        assertNotNull("ProfileKey.TITLE.INSTANCE should be accessible", title);
        assertNotNull("ProfileKey.IMAGE.INSTANCE should be accessible", image);
        assertNotNull("ProfileKey.ADDRESS1.INSTANCE should be accessible", address1);
        assertNotNull("ProfileKey.ADDRESS2.INSTANCE should be accessible", address2);
        assertNotNull("ProfileKey.CITY.INSTANCE should be accessible", city);
        assertNotNull("ProfileKey.COUNTRY.INSTANCE should be accessible", country);
        assertNotNull("ProfileKey.LATITUDE.INSTANCE should be accessible", latitude);
        assertNotNull("ProfileKey.LONGITUDE.INSTANCE should be accessible", longitude);
        assertNotNull("ProfileKey.REGION.INSTANCE should be accessible", region);
        assertNotNull("ProfileKey.ZIP.INSTANCE should be accessible", zip);
        assertNotNull("ProfileKey.TIMEZONE.INSTANCE should be accessible", timezone);

        // Custom ProfileKey - no .INSTANCE needed (it's a class, not object)
        ProfileKey customKey = new ProfileKey.CUSTOM("my_custom_field");
        assertNotNull("ProfileKey.CUSTOM should be instantiable", customKey);
    }

    /**
     * Test EventMetric sealed class object members require .INSTANCE from Java.
     */
    @Test
    public void testEventMetricObjectMembersRequireInstance() {
        // Built-in EventMetric objects - Java must use .INSTANCE
        EventMetric openedApp = EventMetric.OPENED_APP.INSTANCE;
        EventMetric viewedProduct = EventMetric.VIEWED_PRODUCT.INSTANCE;
        EventMetric addedToCart = EventMetric.ADDED_TO_CART.INSTANCE;
        EventMetric startedCheckout = EventMetric.STARTED_CHECKOUT.INSTANCE;

        assertNotNull("EventMetric.OPENED_APP.INSTANCE should be accessible", openedApp);
        assertNotNull("EventMetric.VIEWED_PRODUCT.INSTANCE should be accessible", viewedProduct);
        assertNotNull("EventMetric.ADDED_TO_CART.INSTANCE should be accessible", addedToCart);
        assertNotNull("EventMetric.STARTED_CHECKOUT.INSTANCE should be accessible", startedCheckout);

        // Custom EventMetric - no .INSTANCE needed (it's a class, not object)
        EventMetric customMetric = new EventMetric.CUSTOM("My Custom Metric");
        assertNotNull("EventMetric.CUSTOM should be instantiable", customMetric);
    }

    /**
     * Test EventKey sealed class object members require .INSTANCE from Java.
     */
    @Test
    public void testEventKeyObjectMembersRequireInstance() {
        // Built-in EventKey objects - Java must use .INSTANCE
        EventKey eventId = EventKey.EVENT_ID.INSTANCE;
        EventKey value = EventKey.VALUE.INSTANCE;

        assertNotNull("EventKey.EVENT_ID.INSTANCE should be accessible", eventId);
        assertNotNull("EventKey.VALUE.INSTANCE should be accessible", value);

        // Custom EventKey - no .INSTANCE needed (it's a class, not object)
        EventKey customKey = new EventKey.CUSTOM("my_custom_key");
        assertNotNull("EventKey.CUSTOM should be instantiable", customKey);
    }

    /**
     * Test ProfileKey.Companion is accessible from Java.
     */
    @Test
    public void testProfileKeyCompanionAccessible() {
        // Companion object is accessible
        assertNotNull("ProfileKey.Companion should be accessible", ProfileKey.Companion);

        // IDENTIFIERS set should be accessible
        assertNotNull("ProfileKey.IDENTIFIERS should be accessible", ProfileKey.Companion.getIDENTIFIERS());
    }
}
