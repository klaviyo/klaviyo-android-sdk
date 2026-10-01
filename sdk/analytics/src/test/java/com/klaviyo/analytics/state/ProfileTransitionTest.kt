package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTransitionTest {

    private fun ids(
        externalId: String? = null,
        email: String? = null,
        phoneNumber: String? = null,
        anonymousId: String? = ANON_A
    ) = ProfileIdentifiers(externalId, email, phoneNumber, anonymousId)

    private fun assertTransition(
        expected: ProfileTransition,
        previous: ProfileIdentifiers,
        next: ProfileIdentifiers
    ) = assertEquals(expected, classify(previous, next))

    @Test
    fun `all four identifiers equal is unchanged`() = assertTransition(
        ProfileTransition.UNCHANGED,
        ids(EXTERNAL_ID, EMAIL_A, PHONE_A),
        ids(EXTERNAL_ID, EMAIL_A, PHONE_A)
    )

    @Test
    fun `anonymous to identified is a replacement`() = assertTransition(
        ProfileTransition.REPLACEMENT,
        ids(),
        ids(email = EMAIL_A)
    )

    @Test
    fun `same email with a new anonymous ID is compatible`() = assertTransition(
        ProfileTransition.COMPATIBLE,
        ids(email = EMAIL_A, anonymousId = ANON_A),
        ids(email = EMAIL_A, anonymousId = ANON_B)
    )

    @Test
    fun `email to phone with no overlap is a replacement`() = assertTransition(
        ProfileTransition.REPLACEMENT,
        ids(email = EMAIL_A),
        ids(phoneNumber = PHONE_A)
    )

    @Test
    fun `same email with a different phone is a replacement`() = assertTransition(
        ProfileTransition.REPLACEMENT,
        ids(email = EMAIL_A, phoneNumber = PHONE_A),
        ids(email = EMAIL_A, phoneNumber = PHONE_B)
    )

    @Test
    fun `same email with a different external ID is a replacement`() = assertTransition(
        ProfileTransition.REPLACEMENT,
        ids(externalId = EXTERNAL_ID, email = EMAIL_A),
        ids(externalId = OTHER_EXTERNAL_ID, email = EMAIL_A)
    )

    @Test
    fun `phone gaining an email is compatible`() = assertTransition(
        ProfileTransition.COMPATIBLE,
        ids(phoneNumber = PHONE_A),
        ids(phoneNumber = PHONE_A, email = EMAIL_A)
    )

    @Test
    fun `email losing an external ID is compatible`() = assertTransition(
        ProfileTransition.COMPATIBLE,
        ids(externalId = EXTERNAL_ID, email = EMAIL_A),
        ids(email = EMAIL_A)
    )

    @Test
    fun `one-sided identifiers are ignored when a shared identifier is equal`() = assertTransition(
        ProfileTransition.COMPATIBLE,
        ids(externalId = EXTERNAL_ID, email = EMAIL_A),
        ids(email = EMAIL_A, phoneNumber = PHONE_A)
    )

    @Test
    fun `anonymous to a different anonymous ID is a replacement`() = assertTransition(
        ProfileTransition.REPLACEMENT,
        ids(anonymousId = ANON_A),
        ids(anonymousId = ANON_B)
    )

    @Test
    fun `identified to anonymous is a replacement`() = assertTransition(
        ProfileTransition.REPLACEMENT,
        ids(email = EMAIL_A),
        ids()
    )

    @Test
    fun `case-only email change is a replacement`() = assertTransition(
        ProfileTransition.REPLACEMENT,
        ids(email = "A@example.com"),
        ids(email = EMAIL_A)
    )

    @Test
    fun `empty values are absent`() = assertTransition(
        ProfileTransition.UNCHANGED,
        ids(externalId = "", email = EMAIL_A),
        ids(externalId = null, email = EMAIL_A)
    )

    @Test
    fun `values are compared as state stores them, trimmed`() = assertTransition(
        ProfileTransition.UNCHANGED,
        ids(email = " $EMAIL_A "),
        ids(email = EMAIL_A)
    )

    @Test
    fun `withIdentifier replaces only the given identifier`() {
        val identifiers = ids(externalId = EXTERNAL_ID, email = EMAIL_A)

        val replaced = identifiers.withIdentifier(ProfileKey.EMAIL, EMAIL_B)

        assertEquals(EXTERNAL_ID, replaced.externalId)
        assertEquals(EMAIL_B, replaced.email)
        assertEquals(ANON_A, replaced.anonymousId)
        assertEquals(
            ANON_B,
            identifiers.withIdentifier(ProfileKey.ANONYMOUS_ID, ANON_B).anonymousId
        )
        assertTransition(
            ProfileTransition.UNCHANGED,
            identifiers,
            identifiers.withIdentifier(ProfileKey.FIRST_NAME, "Kermit")
        )
    }

    @Test
    fun `isIdentified ignores the anonymous ID`() {
        assertFalse(ids().isIdentified)
        assertFalse(ids(email = " ").isIdentified)
        assertTrue(ids(phoneNumber = PHONE_A).isIdentified)
    }

    private companion object {
        const val EMAIL_A = "a@example.com"
        const val EMAIL_B = "b@example.com"
        const val PHONE_A = "+15555550123"
        const val PHONE_B = "+15555550124"
        const val EXTERNAL_ID = "stable"
        const val OTHER_EXTERNAL_ID = "other"
        const val ANON_A = "anon-a"
        const val ANON_B = "anon-b"
    }
}
