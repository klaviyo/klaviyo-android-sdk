package com.klaviyo.location;

import com.klaviyo.analytics.Klaviyo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests to verify the Geofencing API is accessible from Java.
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

    @Test
    public void testRegisterGeofencing() {
        Klaviyo result = GeofencingKt.registerGeofencing(Klaviyo.INSTANCE);

        assertEquals(Klaviyo.INSTANCE, result);
        GeofencingMock.verifyRegisterGeofencingCalled();
    }

    @Test
    public void testUnregisterGeofencing() {
        Klaviyo result = GeofencingKt.unregisterGeofencing(Klaviyo.INSTANCE);

        assertEquals(Klaviyo.INSTANCE, result);
        GeofencingMock.verifyUnregisterGeofencingCalled();
    }

    @Test
    public void testKlaviyoInstanceAccessibleForExtensions() {
        assertNotNull(Klaviyo.INSTANCE);
    }

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
