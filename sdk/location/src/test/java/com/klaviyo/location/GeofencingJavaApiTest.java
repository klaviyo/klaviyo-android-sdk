package com.klaviyo.location;

import com.klaviyo.analytics.Klaviyo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests to verify the Geofencing API is accessible from Java.
 *
 * This file contains tests for both:
 * 1. The legacy extension function syntax: GeofencingKt.methodName(Klaviyo.INSTANCE)
 * 2. The new static API syntax: KlaviyoLocation.methodName()
 *
 * The KlaviyoLocation static API is the recommended approach for Java developers.
 */
public class GeofencingJavaApiTest {

    @Before
    public void setup() {
        GeofencingMock.setup();
    }

    @After
    public void teardown() {
        GeofencingMock.teardown();
    }

    /**
     * Test registerGeofencing method.
     * Current Java syntax: GeofencingKt.registerGeofencing(Klaviyo.INSTANCE)
     * Desired syntax: Klaviyo.registerGeofencing()
     */
    @Test
    public void testRegisterGeofencing() {
        Klaviyo result = GeofencingKt.registerGeofencing(Klaviyo.INSTANCE);

        GeofencingMock.verifyRegisterGeofencingCalled();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test unregisterGeofencing method.
     * Current Java syntax: GeofencingKt.unregisterGeofencing(Klaviyo.INSTANCE)
     * Desired syntax: Klaviyo.unregisterGeofencing()
     */
    @Test
    public void testUnregisterGeofencing() {
        Klaviyo result = GeofencingKt.unregisterGeofencing(Klaviyo.INSTANCE);

        GeofencingMock.verifyUnregisterGeofencingCalled();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Verify that Klaviyo.INSTANCE is accessible for use with extension functions.
     */
    @Test
    public void testKlaviyoInstanceAccessibleForExtensions() {
        assertNotNull("Klaviyo.INSTANCE should be accessible", Klaviyo.INSTANCE);
    }

    // ==================== KlaviyoLocation Static API Tests ====================

    @Test
    public void testKlaviyoLocationRegister() {
        KlaviyoLocation.registerGeofencing();
        GeofencingMock.verifyKlaviyoLocationRegisterCalled();
    }

    @Test
    public void testKlaviyoLocationUnregister() {
        KlaviyoLocation.unregisterGeofencing();
        GeofencingMock.verifyKlaviyoLocationUnregisterCalled();
    }
}
