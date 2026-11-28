package com.klaviyo.forms;

import com.klaviyo.analytics.Klaviyo;
import com.klaviyo.core.MissingKlaviyoModule;
import com.klaviyo.fixtures.BaseTest;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class OpenFormJavaApiTest extends BaseTest {
    @Test
    public void manualFormApisAreCallableFromJava() {
        assertThrows(MissingKlaviyoModule.class, () -> KlaviyoForms.openForm("form"));
        assertThrows(MissingKlaviyoModule.class, () -> InAppFormsKt.openForm(Klaviyo.INSTANCE, "form"));
    }
}
