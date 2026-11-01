package org.viptv.app

import org.viptv.app.components.CredentialFields
import org.viptv.app.components.autocompleteCurrentPassword
import org.viptv.app.components.autocompleteName
import org.viptv.app.components.autocompleteNewPassword
import org.viptv.app.components.autocompleteOff
import org.viptv.app.components.autocompleteUsername
import kotlin.test.*

class CredentialFieldsTest {
    @Test fun signInOffersUsernameAndCurrentPassword() {
        val fields = CredentialFields.signIn("someone", "secret")
        assertEquals(listOf("username", "password"), fields.map { it.name })
        assertEquals(listOf(autocompleteUsername, autocompleteCurrentPassword), fields.map { it.autocomplete })
        assertEquals(listOf(false, true), fields.map { it.secret })
    }

    @Test fun accountCreationUsesNewPasswordForBothPasswordFields() {
        val fields = CredentialFields.register("someone", "Someone", "secret-12", "secret-12", null)
        assertEquals(listOf(autocompleteUsername, autocompleteName, autocompleteNewPassword, autocompleteNewPassword), fields.map { it.autocomplete })
        assertEquals(listOf(false, false, true, true), fields.map { it.secret })
    }

    @Test fun recoveryKeepsTheCodeSecretAndAutocompletesOnlyTheNewPasswords() {
        val fields = CredentialFields.recover("someone", "code-1", "next-password-12", "next-password-12", null)
        assertEquals(listOf(autocompleteUsername, autocompleteOff, autocompleteNewPassword, autocompleteNewPassword), fields.map { it.autocomplete })
        assertTrue(fields[1].secret)
    }

    @Test fun changePasswordAutocompletesCurrentThenNewPasswords() {
        val fields = CredentialFields.changePassword("current", null, "next-password-12", "next-password-12", null)
        assertEquals(listOf(autocompleteCurrentPassword, autocompleteNewPassword, autocompleteNewPassword), fields.map { it.autocomplete })
        assertEquals(listOf(true, true, true), fields.map { it.secret })
    }
}