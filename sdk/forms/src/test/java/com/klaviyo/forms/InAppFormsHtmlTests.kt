package com.klaviyo.forms

import org.junit.Assert.assertEquals
import org.junit.Test

class InAppFormsHtmlTests {
    @Test
    fun `Verify IAF handshake`() {
        assertEquals(
            """
                [
                  {
                    "type": "formWillAppear",
                    "version": 1
                  },
                  {
                    "type": "formDisappeared",
                    "version": 1
                  },
                  {
                    "type": "trackProfileEvent",
                    "version": 1
                  },
                  {
                    "type": "trackAggregateEvent",
                    "version": 1
                  },
                  {
                    "type": "openDeepLink",
                    "version": 1
                  },
                  {
                    "type": "abort",
                    "version": 1
                  }
                ]
            """.replace("\\s".toRegex(), ""),
            IAF_HANDSHAKE
        )
    }
}
