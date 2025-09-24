package com.klaviyo.fixtures

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot

/**
 * Data class to hold all Intent capturing slots for easy access and destructuring
 */
data class MockIntent(
    val intent: Intent,
    val action: CapturingSlot<String> = slot(),
    val data: CapturingSlot<Uri> = slot(),
    val packageName: CapturingSlot<String> = slot(),
    val flags: CapturingSlot<Int> = slot(),
    val className: CapturingSlot<String> = slot(),
    val bundle: Bundle = mockk<Bundle>()
) {
    companion object {
        /**
         * Sets up mocking for the Intent class to capture values set via setter methods.
         * Returns an [MockIntent] utility for tests to verify that Intents are constructed
         * or mutated with the expected values.
         *
         * @return IntentMock containing the mocked Intent and capturing slots
         */
        @SuppressLint("WrongConstant")
        fun setupIntentMocking(): MockIntent {
            val mockIntent = MockIntent(mockk<Intent>(relaxed = true)).apply {
                every { intent.addFlags(any()) } returns intent
            }
            val (intent, action, data, packageName, flags, className, bundle) = mockIntent

            mockkConstructor(Intent::class)

            // Mock setter methods to capture values
            every { anyConstructed<Intent>().setClassName(capture(packageName), capture(className)) } returns intent
            every { anyConstructed<Intent>().setData(capture(data)) } returns intent
            every { anyConstructed<Intent>().setAction(capture(action)) } returns intent
            every { anyConstructed<Intent>().setPackage(capture(packageName)) } returns intent
            every { anyConstructed<Intent>().setFlags(capture(flags)) } returns intent
            every { anyConstructed<Intent>().putExtras(any<Bundle>()) } returns intent
            every { anyConstructed<Intent>().addFlags(any()) } returns intent
            every { anyConstructed<Intent>().extras } returns bundle

            // Mock getter methods to return captured values
            every { anyConstructed<Intent>().action } answers {
                if (action.isCaptured) action.captured else ""
            }
            every { anyConstructed<Intent>().data } answers {
                if (data.isCaptured) data.captured else null
            }
            every { anyConstructed<Intent>().`package` } answers {
                if (packageName.isCaptured) packageName.captured else ""
            }
            every { anyConstructed<Intent>().flags } answers {
                if (flags.isCaptured) flags.captured else 0
            }

            return mockIntent
        }

        fun mockIntentWith(payload: Map<String, String>, uri: Uri? = null): MockIntent {
            val mockIntent = setupIntentMocking()
            val intent = mockIntent.intent
            val bundle = mockk<Bundle>()
            every { intent.data } returns uri
            every { intent.extras } returns bundle
            every { bundle.keySet() } returns payload.keys
            every { intent.getStringExtra(any()) } answers { call -> payload[call.invocation.args[0]] }
            every {
                bundle.getString(
                    any(),
                    String()
                )
            } answers { call -> payload[call.invocation.args[0]] }

            return mockIntent
        }
    }
}
