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
     * Test InAppFormsConfig with custom timeout using Kotlin helper.
     * Demonstrates the workaround for Java not being able to create Duration values.
     *
     * IMPORTANT: kotlin.time.Duration is an inline/value class, which means:
     * - Java CANNOT directly call methods that accept or return Duration
     * - The constructor InAppFormsConfig(Duration) is not callable from Java
     * - Methods like getSessionTimeoutDuration() are mangled and inaccessible
     *
     * Use the Kotlin helper methods in InAppFormsMock to create configs from Java.
     */
    @Test
    public void testInAppFormsConfigWithCustomTimeout() {
        // Create config with 30 minute timeout (1800 seconds)
        InAppFormsConfig config = InAppFormsMock.createConfigWithTimeoutSeconds(1800);
        assertNotNull("InAppFormsConfig with custom timeout should be instantiable", config);

        // Note: We cannot call config.getSessionTimeoutDuration() from Java
        // because Duration is an inline class and the method name is mangled
    }

    /**
     * Test InAppFormsConfig with infinite timeout.
     * Use this when you never want the forms session to timeout.
     */
    @Test
    public void testInAppFormsConfigWithInfiniteTimeout() {
        InAppFormsConfig config = InAppFormsMock.createConfigWithInfiniteTimeout();
        assertNotNull("InAppFormsConfig with infinite timeout should be instantiable", config);
    }

    /**
     * Test InAppFormsConfig with zero timeout.
     * Use this when you want forms to timeout immediately on app background.
     */
    @Test
    public void testInAppFormsConfigWithZeroTimeout() {
        InAppFormsConfig config = InAppFormsMock.createConfigWithZeroTimeout();
        assertNotNull("InAppFormsConfig with zero timeout should be instantiable", config);
    }

    /**
     * Test that Companion object is accessible but Duration methods are not.
     *
     * IMPORTANT: DEFAULT_SESSION_TIMEOUT returns kotlin.time.Duration (inline class),
     * so getDEFAULT_SESSION_TIMEOUT() is NOT callable from Java - the method name is mangled.
     *
     * This is a known limitation of Kotlin inline/value classes in Java interop.
     */
    @Test
    public void testCompanionAccessibleButDurationMethodsAreNot() {
        // Companion object is accessible
        assertNotNull("InAppFormsConfig.Companion should be accessible",
            InAppFormsConfig.Companion);

        // But we CANNOT call getDEFAULT_SESSION_TIMEOUT() from Java:
        // InAppFormsConfig.Companion.getDEFAULT_SESSION_TIMEOUT() // DOES NOT COMPILE
        // The actual method is mangled to: getDEFAULT_SESSION_TIMEOUT-UwyO8pc()
    }

    /**
     * Verify that Klaviyo.INSTANCE is accessible for use with extension functions.
     */
    @Test
    public void testKlaviyoInstanceAccessibleForExtensions() {
        assertNotNull("Klaviyo.INSTANCE should be accessible", Klaviyo.INSTANCE);
    }
}
