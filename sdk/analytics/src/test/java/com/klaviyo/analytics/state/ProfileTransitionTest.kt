package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTransitionTest {

    private fun transition(current: Profile, next: Profile): ProfileTransition =
        current.profileIdentifiers.transitionTo(next.profileIdentifiers)

    @Test
    fun `compatible addition does not replace the profile`() {
        assertEquals(
            ProfileTransition.COMPATIBLE,
            transition(Profile(email = EMAIL_A), Profile(email = EMAIL_A, externalId = EXTERNAL_ID))
        )
    }

    @Test
    fun `conflicting identifier replaces the profile`() {
        assertEquals(
            ProfileTransition.REPLACEMENT,
            transition(Profile(email = EMAIL_A), Profile(email = EMAIL_B))
        )
    }

    @Test
    fun `anonymous identifier change does not affect the transition`() {
        val current = Profile(email = EMAIL_A).apply { anonymousId = "previous" }
        val next = current.copy().apply { anonymousId = "current" }

        assertEquals(ProfileTransition.UNCHANGED, transition(current, next))
    }

    @Test
    fun `identifier transition matrix`() {
        val cases = listOf(
            Triple(Profile(), Profile(), ProfileTransition.UNCHANGED),
            // anonymous to identified
            Triple(Profile(), Profile(email = EMAIL_A), ProfileTransition.REPLACEMENT),
            // identified to anonymous
            Triple(Profile(email = EMAIL_A), Profile(), ProfileTransition.REPLACEMENT),
            Triple(
                Profile(email = EMAIL_A),
                Profile(email = EMAIL_A, phoneNumber = PHONE_A),
                ProfileTransition.COMPATIBLE
            ),
            Triple(
                Profile(email = EMAIL_A, phoneNumber = PHONE_A),
                Profile(email = EMAIL_A),
                ProfileTransition.COMPATIBLE
            ),
            Triple(
                Profile(email = EMAIL_A, externalId = EXTERNAL_ID),
                Profile(email = EMAIL_A, phoneNumber = PHONE_A),
                ProfileTransition.COMPATIBLE
            ),
            Triple(
                Profile(email = EMAIL_A),
                Profile(email = EMAIL_B),
                ProfileTransition.REPLACEMENT
            ),
            // case-only email change
            Triple(
                Profile(email = "A@example.com"),
                Profile(email = EMAIL_A),
                ProfileTransition.REPLACEMENT
            ),
            // no shared identifier
            Triple(
                Profile(email = EMAIL_A),
                Profile(phoneNumber = PHONE_A),
                ProfileTransition.REPLACEMENT
            ),
            // whitespace is trimmed
            Triple(
                Profile(email = " $EMAIL_A "),
                Profile(email = EMAIL_A),
                ProfileTransition.UNCHANGED
            ),
            // blank is absent
            Triple(
                Profile(email = EMAIL_A, externalId = "  "),
                Profile(email = EMAIL_A),
                ProfileTransition.UNCHANGED
            ),
            Triple(
                Profile(email = EMAIL_A, phoneNumber = PHONE_A),
                Profile(email = EMAIL_A, phoneNumber = PHONE_B),
                ProfileTransition.REPLACEMENT
            )
        )

        cases.forEachIndexed { index, (current, next, expected) ->
            assertEquals("case $index", expected, transition(current, next))
        }
    }

    @Test
    fun `withIdentifier replaces only the given identifier`() {
        val identifiers = Profile(externalId = EXTERNAL_ID, email = EMAIL_A).profileIdentifiers

        val replaced = identifiers.withIdentifier(ProfileKey.EMAIL, EMAIL_B)

        assertEquals(EXTERNAL_ID, replaced.externalId)
        assertEquals(EMAIL_B, replaced.email)
        assertEquals(
            ProfileTransition.UNCHANGED,
            identifiers.withIdentifier(ProfileKey.ANONYMOUS_ID, "anon").transitionTo(identifiers)
        )
    }

    @Test
    fun `isIdentified reflects presence of any identifier`() {
        assertFalse(Profile().profileIdentifiers.isIdentified)
        assertFalse(Profile(email = " ").profileIdentifiers.isIdentified)
        assertTrue(Profile(phoneNumber = PHONE_A).profileIdentifiers.isIdentified)
    }

    private companion object {
        const val EMAIL_A = "a@example.com"
        const val EMAIL_B = "b@example.com"
        const val PHONE_A = "+15555550123"
        const val PHONE_B = "+15555550124"
        const val EXTERNAL_ID = "stable"
    }
}
