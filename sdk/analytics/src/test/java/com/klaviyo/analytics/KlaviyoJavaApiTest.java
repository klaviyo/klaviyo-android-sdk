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
 * Each test verifies both syntaxes work:
 * 1. Legacy INSTANCE syntax: Klaviyo.INSTANCE.methodName() - for backward compatibility
 * 2. Static syntax: Klaviyo.methodName() - the recommended approach
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
     * Test initialize method with both syntaxes.
     */
    @Test
    public void testInitialize() {
        String apiKey = "test-api-key";

        // Legacy INSTANCE syntax (backward compatibility)
        Klaviyo result1 = Klaviyo.INSTANCE.initialize(apiKey, mockContext);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax (recommended)
        Klaviyo result2 = Klaviyo.initialize(apiKey, mockContext);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test registerForLifecycleCallbacks method with both syntaxes.
     */
    @Test
    public void testRegisterForLifecycleCallbacks() {
        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.registerForLifecycleCallbacks(mockContext);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.registerForLifecycleCallbacks(mockContext);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test setProfile method with both syntaxes.
     */
    @Test
    public void testSetProfile() {
        Profile profile = new Profile(null, null, null, null);

        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.setProfile(profile);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.setProfile(profile);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test setEmail method with both syntaxes.
     */
    @Test
    public void testSetEmail() {
        String email = "test@example.com";

        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.setEmail(email);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.setEmail(email);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test getEmail method with both syntaxes.
     */
    @Test
    public void testGetEmail() {
        // Legacy INSTANCE syntax
        String email1 = Klaviyo.INSTANCE.getEmail();
        assertNotNull("getEmail should return a value", email1);

        // Static syntax
        String email2 = Klaviyo.getEmail();
        assertNotNull("getEmail should return a value", email2);
    }

    /**
     * Test setPhoneNumber method with both syntaxes.
     */
    @Test
    public void testSetPhoneNumber() {
        String phone = "+15555555555";

        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.setPhoneNumber(phone);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.setPhoneNumber(phone);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test getPhoneNumber method with both syntaxes.
     */
    @Test
    public void testGetPhoneNumber() {
        // Legacy INSTANCE syntax
        String phone1 = Klaviyo.INSTANCE.getPhoneNumber();
        assertNotNull("getPhoneNumber should return a value", phone1);

        // Static syntax
        String phone2 = Klaviyo.getPhoneNumber();
        assertNotNull("getPhoneNumber should return a value", phone2);
    }

    /**
     * Test setExternalId method with both syntaxes.
     */
    @Test
    public void testSetExternalId() {
        String externalId = "ext-123";

        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.setExternalId(externalId);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.setExternalId(externalId);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test getExternalId method with both syntaxes.
     */
    @Test
    public void testGetExternalId() {
        // Legacy INSTANCE syntax
        String externalId1 = Klaviyo.INSTANCE.getExternalId();
        assertNotNull("getExternalId should return a value", externalId1);

        // Static syntax
        String externalId2 = Klaviyo.getExternalId();
        assertNotNull("getExternalId should return a value", externalId2);
    }

    /**
     * Test setPushToken method with both syntaxes.
     */
    @Test
    public void testSetPushToken() {
        String token = "push-token-abc";

        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.setPushToken(token);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.setPushToken(token);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test getPushToken method with both syntaxes.
     */
    @Test
    public void testGetPushToken() {
        // Legacy INSTANCE syntax
        String token1 = Klaviyo.INSTANCE.getPushToken();
        assertNotNull("getPushToken should return a value", token1);

        // Static syntax
        String token2 = Klaviyo.getPushToken();
        assertNotNull("getPushToken should return a value", token2);
    }

    /**
     * Test setProfileAttribute method with both syntaxes.
     */
    @Test
    public void testSetProfileAttribute() {
        ProfileKey key = ProfileKey.FIRST_NAME.INSTANCE;
        String value = "John";

        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.setProfileAttribute(key, value);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.setProfileAttribute(key, value);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test setProfileAttribute with a custom ProfileKey using both syntaxes.
     */
    @Test
    public void testSetProfileAttributeCustomKey() {
        ProfileKey customKey = new ProfileKey.CUSTOM("custom_field");
        String value = "custom_value";

        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.setProfileAttribute(customKey, value);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.setProfileAttribute(customKey, value);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test resetProfile method with both syntaxes.
     */
    @Test
    public void testResetProfile() {
        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.resetProfile();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.resetProfile();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test createEvent method with Event object using both syntaxes.
     */
    @Test
    public void testCreateEventWithEvent() {
        Event event = new Event(EventMetric.VIEWED_PRODUCT.INSTANCE);

        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.createEvent(event);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.createEvent(event);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test createEvent method with EventMetric and value using both syntaxes.
     */
    @Test
    public void testCreateEventWithMetric() {
        EventMetric metric = EventMetric.ADDED_TO_CART.INSTANCE;

        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.createEvent(metric, 19.99);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.createEvent(metric, 19.99);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test createEvent with custom EventMetric using both syntaxes.
     */
    @Test
    public void testCreateEventWithCustomMetric() {
        EventMetric customMetric = new EventMetric.CUSTOM("Custom Event");

        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.createEvent(customMetric, null);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.createEvent(customMetric, null);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test handlePush method with both syntaxes.
     */
    @Test
    public void testHandlePush() {
        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.handlePush(mockIntent);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.handlePush(mockIntent);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test handleUniversalTrackingLink with String URL using both syntaxes.
     */
    @Test
    public void testHandleUniversalTrackingLinkString() {
        String url = "https://trk.klviyomail.com/test";

        // Legacy INSTANCE syntax
        boolean result1 = Klaviyo.INSTANCE.handleUniversalTrackingLink(url);
        assertTrue("Should return true for tracking link", result1);

        // Static syntax
        boolean result2 = Klaviyo.handleUniversalTrackingLink(url);
        assertTrue("Should return true for tracking link", result2);
    }

    /**
     * Test handleUniversalTrackingLink with Intent using both syntaxes.
     */
    @Test
    public void testHandleUniversalTrackingLinkIntent() {
        // Legacy INSTANCE syntax
        boolean result1 = Klaviyo.INSTANCE.handleUniversalTrackingLink(mockIntent);
        assertTrue("Should return true for tracking link", result1);

        // Static syntax
        boolean result2 = Klaviyo.handleUniversalTrackingLink(mockIntent);
        assertTrue("Should return true for tracking link", result2);
    }

    /**
     * Test registerDeepLinkHandler method with both syntaxes.
     */
    @Test
    public void testRegisterDeepLinkHandler() {
        DeepLinkHandler handler = uri -> {
            // Handle deep link
        };

        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.registerDeepLinkHandler(handler);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.registerDeepLinkHandler(handler);
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test unregisterDeepLinkHandler method with both syntaxes.
     */
    @Test
    public void testUnregisterDeepLinkHandler() {
        // Legacy INSTANCE syntax
        Klaviyo result1 = Klaviyo.INSTANCE.unregisterDeepLinkHandler();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result1);

        // Static syntax
        Klaviyo result2 = Klaviyo.unregisterDeepLinkHandler();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result2);
    }

    /**
     * Test deprecated isKlaviyoIntent with both syntaxes.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testIsKlaviyoIntent() {
        // Legacy INSTANCE syntax
        boolean result1 = Klaviyo.INSTANCE.isKlaviyoIntent(mockIntent);
        assertTrue("Should return true", result1);

        // Static syntax
        boolean result2 = Klaviyo.isKlaviyoIntent(mockIntent);
        assertTrue("Should return true", result2);
    }

    /**
     * Test isKlaviyoNotificationIntent with both syntaxes.
     */
    @Test
    public void testIsKlaviyoNotificationIntent() {
        // Legacy INSTANCE syntax
        boolean result1 = Klaviyo.INSTANCE.isKlaviyoNotificationIntent(mockIntent);
        assertTrue("Should return true", result1);

        // Static syntax
        boolean result2 = Klaviyo.isKlaviyoNotificationIntent(mockIntent);
        assertTrue("Should return true", result2);
    }

    /**
     * Test isKlaviyoUniversalTrackingIntent with both syntaxes.
     */
    @Test
    public void testIsKlaviyoUniversalTrackingIntent() {
        // Legacy INSTANCE syntax
        boolean result1 = Klaviyo.INSTANCE.isKlaviyoUniversalTrackingIntent(mockIntent);
        assertTrue("Should return true", result1);

        // Static syntax
        boolean result2 = Klaviyo.isKlaviyoUniversalTrackingIntent(mockIntent);
        assertTrue("Should return true", result2);
    }

    /**
     * Test isKlaviyoUniversalTrackingUri with both syntaxes.
     */
    @Test
    public void testIsKlaviyoUniversalTrackingUri() {
        // Legacy INSTANCE syntax
        boolean result1 = Klaviyo.INSTANCE.isKlaviyoUniversalTrackingUri(mockUri);
        assertTrue("Should return true", result1);

        // Static syntax
        boolean result2 = Klaviyo.isKlaviyoUniversalTrackingUri(mockUri);
        assertTrue("Should return true", result2);
    }

    /**
     * Test method chaining works with both syntaxes.
     */
    @Test
    public void testMethodChaining() {
        // Legacy INSTANCE syntax
        Klaviyo.INSTANCE
            .setEmail("test@example.com")
            .setPhoneNumber("+15555555555")
            .setExternalId("ext-123");

        // Static syntax (recommended)
        Klaviyo
            .setEmail("test@example.com")
            .setPhoneNumber("+15555555555")
            .setExternalId("ext-123");
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
     * Test Profile fluent setters work from Java.
     *
     * The SDK uses @JvmSynthetic on property setters to hide them from Java,
     * leaving only the fluent setters (which return Profile for chaining).
     */
    @Test
    public void testProfileSetters() {
        // Fluent setters work and allow chaining
        Profile profile = new Profile()
            .setEmail("test@example.com")
            .setPhoneNumber("+15555555555")
            .setExternalId("ext-123");

        assertEquals("test@example.com", profile.getEmail());
        assertEquals("+15555555555", profile.getPhoneNumber());
        assertEquals("ext-123", profile.getExternalId());

        // Constructor with parameters still works
        Profile profile2 = new Profile("ext-456", "other@example.com", "+16666666666", null);
        assertEquals("other@example.com", profile2.getEmail());

        // setProperty also works for custom properties
        Profile profile3 = new Profile()
            .setProperty(ProfileKey.FIRST_NAME.INSTANCE, "John")
            .setProperty("custom_field", "custom_value");

        assertNotNull("Profile with setProperty should work", profile3);
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
     * Test Event fluent setters work from Java.
     *
     * The SDK uses @JvmSynthetic on property setters to hide them from Java,
     * leaving only the fluent setters (which return Event for chaining).
     */
    @Test
    public void testEventSetters() {
        // Fluent setters work and allow chaining
        Event event = new Event(EventMetric.STARTED_CHECKOUT.INSTANCE)
            .setValue(99.99)
            .setUniqueId("order-123")
            .setProperty(new EventKey.CUSTOM("items_count"), 3);

        assertEquals(Double.valueOf(99.99), event.getValue());
        assertEquals("order-123", event.getUniqueId());
        assertNotNull("Event with fluent setters should work", event);
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
