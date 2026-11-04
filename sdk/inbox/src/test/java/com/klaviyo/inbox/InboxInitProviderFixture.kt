package com.klaviyo.inbox

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

/**
 * An [InboxInitProvider] attached to [context], as the system attaches a ContentProvider before
 * `onCreate`. A plain JVM test cannot read the provider's real context.
 */
internal fun inboxInitProvider(
    context: Context? = mockk<Context>().apply { every { applicationContext } returns this }
): InboxInitProvider = spyk(InboxInitProvider()).apply {
    every { this@apply.context } returns context
}
