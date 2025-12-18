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
        InAppFormsConfig config = InAppFormsMock.createDefaultConfig();

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
    public void testInAppFormsConfigUsableFromJava() {
        InAppFormsConfig config = InAppFormsMock.createDefaultConfig();
        assertNotNull(config);

        assertNotNull(InAppFormsConfig.Companion);
    }

    @Test
    public void testInAppFormsConfigWithCustomTimeout() {
        InAppFormsConfig config = InAppFormsMock.createConfigWithTimeoutSeconds(1800);
        assertNotNull(config);
    }

    @Test
    public void testInAppFormsConfigWithInfiniteTimeout() {
        InAppFormsConfig config = InAppFormsMock.createConfigWithInfiniteTimeout();
        assertNotNull(config);
    }

    @Test
    public void testInAppFormsConfigWithZeroTimeout() {
        InAppFormsConfig config = InAppFormsMock.createConfigWithZeroTimeout();
        assertNotNull(config);
    }

    @Test
    public void testKlaviyoInstanceAccessibleForExtensions() {
        assertNotNull(Klaviyo.INSTANCE);
    }

    // ==================== KlaviyoForms Static API Tests ====================

    @Test
    public void testKlaviyoFormsRegisterDefault() {
        KlaviyoForms.registerForInAppForms();
        InAppFormsMock.verifyKlaviyoFormsRegisterCalledNoArg();
    }

    @Test
    public void testKlaviyoFormsRegisterWithConfig() {
        InAppFormsConfig config = InAppFormsMock.createDefaultConfig();
        KlaviyoForms.registerForInAppForms(config);
        InAppFormsMock.verifyKlaviyoFormsRegisterCalled();
    }

    @Test
    public void testKlaviyoFormsUnregister() {
        KlaviyoForms.unregisterFromInAppForms();
        InAppFormsMock.verifyKlaviyoFormsUnregisterCalled();
    }
}
