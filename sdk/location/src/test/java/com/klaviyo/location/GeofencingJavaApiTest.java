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
 * These tests demonstrate the current Java syntax required to call the SDK methods.
 * Since registerGeofencing and unregisterGeofencing are Kotlin extension functions
 * on the Klaviyo object, Java callers must use GeofencingKt.methodName(Klaviyo.INSTANCE)
 * to access methods. This is the "ugly but functional" syntax that works today.
 *
 * A future PR will add static wrapper methods to enable the cleaner Klaviyo.methodName() syntax.
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
}
