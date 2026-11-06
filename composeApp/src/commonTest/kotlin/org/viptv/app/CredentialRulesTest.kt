package org.viptv.app

import org.viptv.app.components.CredentialRules
import kotlin.test.*

class CredentialRulesTest {
    @Test fun theUsernameRuleNamesItsSetAndExplainsBothBreaks() {
        val hint = CredentialRules.username("")
        assertEquals("Letters, numbers, or . _ - @. Up to 80 characters.", hint.hint)
        assertNull(hint.error)
        assertFalse(hint.valid)

        val spelled = CredentialRules.username("Bob Smith")
        assertEquals("Use letters, numbers, or . _ - @ only.", spelled.error, "a space is outside the set")
        assertNull(spelled.hint, "the error replaces the hint")
        assertFalse(spelled.valid)

        val long = CredentialRules.username("a".repeat(81))
        assertEquals("Use at most 80 characters.", long.error)
        assertFalse(long.valid)

        assertTrue(CredentialRules.username("a".repeat(80) + " ").valid, "the trimmed name is what counts")

        val accepted = CredentialRules.username("ada.lovelace_1@example-1")
        assertTrue(accepted.valid)
        assertEquals("Letters, numbers, or . _ - @. Up to 80 characters.", accepted.hint)
    }

    @Test fun thePasswordRuleKeepsTheChangePasswordLineAndAddsTheCeiling() {
        assertEquals("At least 12 characters.", CredentialRules.password("").hint)
        assertFalse(CredentialRules.password("").valid)

        val short = CredentialRules.password("eleven-char")
        assertEquals("Use at least 12 characters.", short.error)
        assertFalse(short.valid)
        assertNull(short.hint)

        val long = CredentialRules.password("a".repeat(257))
        assertEquals("Use at most 256 characters.", long.error)
        assertFalse(long.valid)

        val accepted = CredentialRules.password("twelve-chars")
        assertTrue(accepted.valid)
        assertEquals("At least 12 characters.", accepted.hint)
        assertTrue(CredentialRules.password("é".repeat(6)).valid, "the rule counts bytes, so six two-byte letters pass")
    }

    @Test fun theDisplayNameRuleCountsBytes() {
        assertEquals("Up to 80 characters.", CredentialRules.displayName("").hint)
        assertFalse(CredentialRules.displayName("").valid)
        assertEquals("Use at most 80 characters.", CredentialRules.displayName("a".repeat(81)).error)
        assertFalse(CredentialRules.displayName("a".repeat(81)).valid)
        assertTrue(CredentialRules.displayName("a".repeat(80)).valid)
        assertFalse(CredentialRules.displayName("é".repeat(41)).valid, "82 bytes is over the limit")
    }

    @Test fun theRecoveryCodeRuleSaysWhereItCameFromAndItsCeiling() {
        assertEquals("The code you saved when creating your account.", CredentialRules.recoveryCode("").hint)
        assertFalse(CredentialRules.recoveryCode("").valid)
        assertFalse(CredentialRules.recoveryCode("   ").valid)
        assertEquals("Use at most 256 characters.", CredentialRules.recoveryCode("a".repeat(257)).error)
        assertTrue(CredentialRules.recoveryCode("code-1").valid)
    }

    @Test fun theConfirmationRuleOnlySpeaksWhenItDisagrees() {
        val empty = CredentialRules.confirmation("secret-12", "")
        assertNull(empty.hint)
        assertNull(empty.error)
        assertFalse(empty.valid)

        val mismatch = CredentialRules.confirmation("secret-12", "secret-13")
        assertEquals("Passwords do not match.", mismatch.error)
        assertFalse(mismatch.valid)

        assertTrue(CredentialRules.confirmation("secret-12", "secret-12").valid)
    }
}