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

    @Test
    public void testInitialize() {
        String apiKey = "test-api-key";

        Klaviyo result1 = Klaviyo.INSTANCE.initialize(apiKey, mockContext);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.initialize(apiKey, mockContext);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifyInitializeCalled(apiKey, 2);
    }

    @Test
    public void testRegisterForLifecycleCallbacks() {
        Klaviyo result1 = Klaviyo.INSTANCE.registerForLifecycleCallbacks(mockContext);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.registerForLifecycleCallbacks(mockContext);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifyRegisterForLifecycleCallbacksCalled(2);
    }

    @Test
    public void testSetProfile() {
        Profile profile = new Profile();

        Klaviyo result1 = Klaviyo.INSTANCE.setProfile(profile);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.setProfile(profile);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifySetProfileCalled(2);
    }

    @Test
    public void testSetEmail() {
        String email = "test@example.com";

        Klaviyo result1 = Klaviyo.INSTANCE.setEmail(email);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.setEmail(email);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifySetEmailCalled(email, 2);
    }

    @Test
    public void testGetEmail() {
        String email1 = Klaviyo.INSTANCE.getEmail();
        assertNotNull(email1);

        String email2 = Klaviyo.getEmail();
        assertNotNull(email2);
    }

    @Test
    public void testSetPhoneNumber() {
        String phone = "+15555555555";

        Klaviyo result1 = Klaviyo.INSTANCE.setPhoneNumber(phone);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.setPhoneNumber(phone);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifySetPhoneNumberCalled(phone, 2);
    }

    @Test
    public void testGetPhoneNumber() {
        String phone1 = Klaviyo.INSTANCE.getPhoneNumber();
        assertNotNull(phone1);

        String phone2 = Klaviyo.getPhoneNumber();
        assertNotNull(phone2);
    }

    @Test
    public void testSetExternalId() {
        String externalId = "ext-123";

        Klaviyo result1 = Klaviyo.INSTANCE.setExternalId(externalId);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.setExternalId(externalId);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifySetExternalIdCalled(externalId, 2);
    }

    @Test
    public void testGetExternalId() {
        String externalId1 = Klaviyo.INSTANCE.getExternalId();
        assertNotNull(externalId1);

        String externalId2 = Klaviyo.getExternalId();
        assertNotNull(externalId2);
    }

    @Test
    public void testSetPushToken() {
        String token = "push-token-abc";

        Klaviyo result1 = Klaviyo.INSTANCE.setPushToken(token);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.setPushToken(token);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifySetPushTokenCalled(token, 2);
    }

    @Test
    public void testGetPushToken() {
        String token1 = Klaviyo.INSTANCE.getPushToken();
        assertNotNull(token1);

        String token2 = Klaviyo.getPushToken();
        assertNotNull(token2);
    }

    @Test
    public void testSetProfileAttribute() {
        ProfileKey key = ProfileKey.FIRST_NAME.INSTANCE;
        String value = "John";

        Klaviyo result1 = Klaviyo.INSTANCE.setProfileAttribute(key, value);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.setProfileAttribute(key, value);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifySetProfileAttributeCalled(key, value, 2);
    }

    @Test
    public void testSetProfileAttributeCustomKey() {
        ProfileKey customKey = new ProfileKey.CUSTOM("custom_field");
        String value = "custom_value";

        Klaviyo result1 = Klaviyo.INSTANCE.setProfileAttribute(customKey, value);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.setProfileAttribute(customKey, value);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifySetProfileAttributeCalled(customKey, value, 2);
    }

    @Test
    public void testResetProfile() {
        Klaviyo result1 = Klaviyo.INSTANCE.resetProfile();
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.resetProfile();
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifyResetProfileCalled(2);
    }

    @Test
    public void testCreateEventWithEvent() {
        Event event = new Event(EventMetric.VIEWED_PRODUCT.INSTANCE);

        Klaviyo result1 = Klaviyo.INSTANCE.createEvent(event);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.createEvent(event);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifyCreateEventCalled(2);
    }

    @Test
    public void testCreateEventWithMetric() {
        EventMetric metric = EventMetric.ADDED_TO_CART.INSTANCE;

        Klaviyo result1 = Klaviyo.INSTANCE.createEvent(metric, 19.99);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.createEvent(metric, 19.99);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifyCreateEventWithMetricCalled(metric, 2);
    }

    @Test
    public void testCreateEventWithCustomMetric() {
        EventMetric customMetric = new EventMetric.CUSTOM("Custom Event");

        Klaviyo result1 = Klaviyo.INSTANCE.createEvent(customMetric, null);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.createEvent(customMetric, null);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifyCreateEventWithMetricCalled(customMetric, 2);
    }

    @Test
    public void testHandlePush() {
        Klaviyo result1 = Klaviyo.INSTANCE.handlePush(mockIntent);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.handlePush(mockIntent);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifyHandlePushCalled(2);
    }

    @Test
    public void testHandleUniversalTrackingLinkString() {
        String url = "https://trk.klviyomail.com/test";

        boolean result1 = Klaviyo.INSTANCE.handleUniversalTrackingLink(url);
        assertTrue(result1);

        boolean result2 = Klaviyo.handleUniversalTrackingLink(url);
        assertTrue(result2);

        KlaviyoMock.verifyHandleUniversalTrackingLinkStringCalled(url, 2);
    }

    @Test
    public void testHandleUniversalTrackingLinkIntent() {
        boolean result1 = Klaviyo.INSTANCE.handleUniversalTrackingLink(mockIntent);
        assertTrue(result1);

        boolean result2 = Klaviyo.handleUniversalTrackingLink(mockIntent);
        assertTrue(result2);

        KlaviyoMock.verifyHandleUniversalTrackingLinkIntentCalled(2);
    }

    @Test
    public void testRegisterDeepLinkHandler() {
        DeepLinkHandler handler = uri -> {
        };

        Klaviyo result1 = Klaviyo.INSTANCE.registerDeepLinkHandler(handler);
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.registerDeepLinkHandler(handler);
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifyRegisterDeepLinkHandlerCalled(2);
    }

    @Test
    public void testUnregisterDeepLinkHandler() {
        Klaviyo result1 = Klaviyo.INSTANCE.unregisterDeepLinkHandler();
        assertEquals(Klaviyo.INSTANCE, result1);

        Klaviyo result2 = Klaviyo.unregisterDeepLinkHandler();
        assertEquals(Klaviyo.INSTANCE, result2);

        KlaviyoMock.verifyUnregisterDeepLinkHandlerCalled(2);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testIsKlaviyoIntent() {
        boolean result1 = Klaviyo.INSTANCE.isKlaviyoIntent(mockIntent);
        assertTrue(result1);

        boolean result2 = Klaviyo.isKlaviyoIntent(mockIntent);
        assertTrue(result2);

        KlaviyoMock.verifyIsKlaviyoIntentCalled(2);
    }

    @Test
    public void testIsKlaviyoNotificationIntent() {
        boolean result1 = Klaviyo.INSTANCE.isKlaviyoNotificationIntent(mockIntent);
        assertTrue(result1);

        boolean result2 = Klaviyo.isKlaviyoNotificationIntent(mockIntent);
        assertTrue(result2);

        KlaviyoMock.verifyIsKlaviyoNotificationIntentCalled(2);
    }

    @Test
    public void testIsKlaviyoUniversalTrackingIntent() {
        boolean result1 = Klaviyo.INSTANCE.isKlaviyoUniversalTrackingIntent(mockIntent);
        assertTrue(result1);

        boolean result2 = Klaviyo.isKlaviyoUniversalTrackingIntent(mockIntent);
        assertTrue(result2);

        KlaviyoMock.verifyIsKlaviyoUniversalTrackingIntentCalled(2);
    }

    @Test
    public void testIsKlaviyoUniversalTrackingUri() {
        boolean result1 = Klaviyo.INSTANCE.isKlaviyoUniversalTrackingUri(mockUri);
        assertTrue(result1);

        boolean result2 = Klaviyo.isKlaviyoUniversalTrackingUri(mockUri);
        assertTrue(result2);

        KlaviyoMock.verifyIsKlaviyoUniversalTrackingUriCalled(2);
    }

    @Test
    public void testMethodChaining() {
        Klaviyo.INSTANCE
                .setEmail("test@example.com")
                .setPhoneNumber("+15555555555")
                .setExternalId("ext-123");

        Klaviyo
                .setEmail("test@example.com")
                .setPhoneNumber("+15555555555")
                .setExternalId("ext-123");

        KlaviyoMock.verifySetEmailCalled("test@example.com", 2);
        KlaviyoMock.verifySetPhoneNumberCalled("+15555555555", 2);
        KlaviyoMock.verifySetExternalIdCalled("ext-123", 2);
    }

    @Test
    public void testProfileClassUsableFromJava() {
        Profile profile = new Profile();
        assertNotNull(profile);

        Profile profileWithParams = new Profile(
                "ext-123",
                "test@example.com",
                "+15555555555"
        );
        assertNotNull(profileWithParams);
    }

    @Test
    public void testProfileConstructorWithPropertiesMap() {
        Map<ProfileKey, Serializable> properties = new HashMap<>();
        properties.put(ProfileKey.FIRST_NAME.INSTANCE, "John");
        properties.put(ProfileKey.LAST_NAME.INSTANCE, "Doe");
        properties.put(new ProfileKey.CUSTOM("loyalty_points"), 100);

        Profile profile = new Profile(properties);
        assertNotNull(profile);
    }

    @Test
    public void testProfileSetters() {
        Profile profile = new Profile()
                .setEmail("test@example.com")
                .setPhoneNumber("+15555555555")
                .setExternalId("ext-123");

        assertEquals("test@example.com", profile.getEmail());
        assertEquals("+15555555555", profile.getPhoneNumber());
        assertEquals("ext-123", profile.getExternalId());

        Profile profile2 = new Profile("ext-456", "other@example.com", "+16666666666");
        assertEquals("other@example.com", profile2.getEmail());

        Profile profile3 = new Profile()
                .setProperty(ProfileKey.FIRST_NAME.INSTANCE, "John")
                .setProperty("custom_field", "custom_value");
        assertNotNull(profile3);
    }

    @Test
    public void testEventClassUsableFromJava() {
        Event event = new Event(EventMetric.VIEWED_PRODUCT.INSTANCE);
        assertNotNull(event);

        Event customEvent = new Event(new EventMetric.CUSTOM("Custom Event"));
        assertNotNull(customEvent);
    }

    @Test
    public void testEventConstructorWithPropertiesMap() {
        Map<EventKey, Serializable> properties = new HashMap<>();
        properties.put(EventKey.VALUE.INSTANCE, 19.99);
        properties.put(new EventKey.CUSTOM("product_id"), "SKU-123");

        Event event = new Event(EventMetric.ADDED_TO_CART.INSTANCE, properties);
        assertNotNull(event);
    }

    @Test
    public void testEventWithStringMetric() {
        Event event = new Event("My Custom Event");
        assertNotNull(event);

        Map<EventKey, Serializable> properties = new HashMap<>();
        properties.put(new EventKey.CUSTOM("item"), "Widget");
        Event eventWithProps = new Event("Purchase", properties);
        assertNotNull(eventWithProps);
    }

    @Test
    public void testEventSetters() {
        Event event = new Event(EventMetric.STARTED_CHECKOUT.INSTANCE)
                .setValue(99.99)
                .setUniqueId("order-123")
                .setProperty(new EventKey.CUSTOM("items_count"), 3);

        assertEquals(Double.valueOf(99.99), event.getValue());
        assertEquals("order-123", event.getUniqueId());
        assertNotNull(event);
    }

    @Test
    public void testProfileKeyObjectMembersRequireInstance() {
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

        assertNotNull(firstName);
        assertNotNull(lastName);
        assertNotNull(organization);
        assertNotNull(title);
        assertNotNull(image);
        assertNotNull(address1);
        assertNotNull(address2);
        assertNotNull(city);
        assertNotNull(country);
        assertNotNull(latitude);
        assertNotNull(longitude);
        assertNotNull(region);
        assertNotNull(zip);
        assertNotNull(timezone);

        ProfileKey customKey = new ProfileKey.CUSTOM("my_custom_field");
        assertNotNull(customKey);
    }

    @Test
    public void testEventMetricObjectMembersRequireInstance() {
        EventMetric openedApp = EventMetric.OPENED_APP.INSTANCE;
        EventMetric viewedProduct = EventMetric.VIEWED_PRODUCT.INSTANCE;
        EventMetric addedToCart = EventMetric.ADDED_TO_CART.INSTANCE;
        EventMetric startedCheckout = EventMetric.STARTED_CHECKOUT.INSTANCE;

        assertNotNull(openedApp);
        assertNotNull(viewedProduct);
        assertNotNull(addedToCart);
        assertNotNull(startedCheckout);

        EventMetric customMetric = new EventMetric.CUSTOM("My Custom Metric");
        assertNotNull(customMetric);
    }

    @Test
    public void testEventKeyObjectMembersRequireInstance() {
        EventKey eventId = EventKey.EVENT_ID.INSTANCE;
        EventKey value = EventKey.VALUE.INSTANCE;

        assertNotNull(eventId);
        assertNotNull(value);

        EventKey customKey = new EventKey.CUSTOM("my_custom_key");
        assertNotNull(customKey);
    }
}
