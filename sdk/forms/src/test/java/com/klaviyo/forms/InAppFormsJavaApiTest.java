package com.klaviyo.forms;

import com.klaviyo.analytics.Klaviyo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests to verify the In-App Forms API is accessible from Java.
 *
 * These tests demonstrate the current Java syntax required to call the SDK methods.
 * Since registerForInAppForms and unregisterFromInAppForms are Kotlin extension functions
 * on the Klaviyo object, Java callers must use InAppFormsKt.methodName(Klaviyo.INSTANCE, ...)
 * to access methods. This is the "ugly but functional" syntax that works today.
 *
 * A future PR will add static wrapper methods to enable the cleaner Klaviyo.methodName() syntax.
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

    /**
     * Test registerForInAppForms method with config.
     * Current Java syntax: InAppFormsKt.registerForInAppForms(Klaviyo.INSTANCE, config)
     * Desired syntax: Klaviyo.registerForInAppForms(config)
     */
    @Test
    public void testRegisterForInAppFormsWithConfig() {
        // InAppFormsConfig requires Duration parameter - use helper from Kotlin mock
        InAppFormsConfig config = InAppFormsMock.createDefaultConfig();

        Klaviyo result = InAppFormsKt.registerForInAppForms(Klaviyo.INSTANCE, config);

        InAppFormsMock.verifyRegisterForInAppFormsCalled();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test unregisterFromInAppForms method.
     * Current Java syntax: InAppFormsKt.unregisterFromInAppForms(Klaviyo.INSTANCE)
     * Desired syntax: Klaviyo.unregisterFromInAppForms()
     */
    @Test
    public void testUnregisterFromInAppForms() {
        Klaviyo result = InAppFormsKt.unregisterFromInAppForms(Klaviyo.INSTANCE);

        InAppFormsMock.verifyUnregisterFromInAppFormsCalled();
        assertEquals("Should return Klaviyo for chaining", Klaviyo.INSTANCE, result);
    }

    /**
     * Test that InAppFormsConfig is usable from Java.
     * Note: InAppFormsConfig requires a Duration parameter which is awkward from Java.
     * Use the helper method in InAppFormsMock to create configs.
     */
    @Test
    public void testInAppFormsConfigUsableFromJava() {
        // InAppFormsConfig needs Duration - use Kotlin helper
        InAppFormsConfig config = InAppFormsMock.createDefaultConfig();
        assertNotNull("InAppFormsConfig should be instantiable via helper", config);

        // Companion object with DEFAULT_SESSION_TIMEOUT is accessible
        assertNotNull("InAppFormsConfig.Companion should be accessible",
            InAppFormsConfig.Companion);
    }

    /**
     * Verify that Klaviyo.INSTANCE is accessible for use with extension functions.
     */
    @Test
    public void testKlaviyoInstanceAccessibleForExtensions() {
        assertNotNull("Klaviyo.INSTANCE should be accessible", Klaviyo.INSTANCE);
    }
}
