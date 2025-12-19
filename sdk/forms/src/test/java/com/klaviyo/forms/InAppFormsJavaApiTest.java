package com.klaviyo.forms;

import com.klaviyo.analytics.Klaviyo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests to verify the In-App Forms API is accessible from Java.
 */
public class InAppFormsJavaApiTest {

    @Before
    public void setup() {
        InAppFormsMock.setup();
    }

    @After
    public void teardown() {
        InAppFormsMock.teardown();
    }

    @Test
    public void testRegisterForInAppFormsWithConfig() {
        InAppFormsConfig config = new InAppFormsConfig();

        Klaviyo result = InAppFormsKt.registerForInAppForms(Klaviyo.INSTANCE, config);

        assertEquals(Klaviyo.INSTANCE, result);
        InAppFormsMock.verifyRegisterForInAppFormsCalled();
    }

    @Test
    public void testUnregisterFromInAppForms() {
        Klaviyo result = InAppFormsKt.unregisterFromInAppForms(Klaviyo.INSTANCE);

        assertEquals(Klaviyo.INSTANCE, result);
        InAppFormsMock.verifyUnregisterFromInAppFormsCalled();
    }

    @Test
    public void testInAppFormsConfigConstructors() {
        // No-arg constructor uses default 1 hour timeout
        InAppFormsConfig defaultConfig = new InAppFormsConfig();
        assertNotNull(defaultConfig);

        // Int constructor for custom timeout in seconds
        InAppFormsConfig customConfig = new InAppFormsConfig(1800);
        assertNotNull(customConfig);
    }

    @Test
    public void testKlaviyoInstanceAccessibleForExtensions() {
        assertNotNull(Klaviyo.INSTANCE);
    }

    @Test
    public void testKlaviyoFormsRegisterDefault() {
        KlaviyoForms.registerForInAppForms();
        InAppFormsMock.verifyKlaviyoFormsRegisterCalledNoArg();
    }

    @Test
    public void testKlaviyoFormsRegisterWithConfig() {
        InAppFormsConfig config = new InAppFormsConfig(1800);
        KlaviyoForms.registerForInAppForms(config);
        InAppFormsMock.verifyKlaviyoFormsRegisterCalled();
    }

    @Test
    public void testKlaviyoFormsUnregister() {
        KlaviyoForms.unregisterFromInAppForms();
        InAppFormsMock.verifyKlaviyoFormsUnregisterCalled();
    }
}
