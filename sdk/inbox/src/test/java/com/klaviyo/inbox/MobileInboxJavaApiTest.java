package com.klaviyo.inbox;

import com.klaviyo.analytics.Klaviyo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests to verify the Mobile Inbox registration API is accessible from Java.
 */
public class MobileInboxJavaApiTest {

    @Before
    public void setup() {
        MobileInboxMock.setup();
    }

    @After
    public void teardown() {
        MobileInboxMock.teardown();
    }

    @Test
    public void testRegisterForMobileInboxExtension() {
        MobileInboxConfig config = new MobileInboxConfig(250);
        Klaviyo result = MobileInboxRegistrationKt.registerForMobileInbox(Klaviyo.INSTANCE, config);

        assertEquals(Klaviyo.INSTANCE, result);
        MobileInboxMock.verifyRegisterCalled(config);
    }

    @Test
    public void testUnregisterFromMobileInboxExtension() {
        Klaviyo result = MobileInboxRegistrationKt.unregisterFromMobileInbox(Klaviyo.INSTANCE);

        assertEquals(Klaviyo.INSTANCE, result);
        MobileInboxMock.verifyUnregisterCalled();
    }

    @Test
    public void testKlaviyoInboxRegisterWithDefaultConfig() {
        KlaviyoInbox.registerForMobileInbox();
        MobileInboxMock.verifyRegisterCalled(new MobileInboxConfig());
    }

    @Test
    public void testKlaviyoInboxRegisterWithConfig() {
        KlaviyoInbox.registerForMobileInbox(new MobileInboxConfig(250));
        MobileInboxMock.verifyRegisterCalled(new MobileInboxConfig(250));
    }

    @Test
    public void testKlaviyoInboxUnregister() {
        KlaviyoInbox.unregisterFromMobileInbox();
        MobileInboxMock.verifyUnregisterCalled();
    }

    @Test
    public void testConfigJavaConstructors() {
        assertEquals(MobileInboxConfig.DEFAULT_LOCAL_RETENTION_LIMIT, new MobileInboxConfig().getLocalRetentionLimit());
        assertEquals(250, new MobileInboxConfig(250).getLocalRetentionLimit());
        assertEquals(MobileInboxConfig.MAX_LOCAL_RETENTION_LIMIT, 500);
    }
}
