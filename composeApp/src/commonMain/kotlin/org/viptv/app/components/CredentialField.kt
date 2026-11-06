package org.viptv.app.components

import androidx.compose.ui.text.input.ImeAction
import org.viptv.app.connection.validAccountUsername
import org.viptv.app.connection.validNewPassword

internal const val autocompleteUsername = "username"
internal const val autocompleteCurrentPassword = "current-password"
internal const val autocompleteNewPassword = "new-password"
internal const val autocompleteName = "name"
internal const val autocompleteOff = "off"

internal data class CredentialField(
    val name: String,
    val label: String,
    val autocomplete: String,
    val secret: Boolean,
    val value: String,
    val errorText: String? = null,
    val hintBelow: String? = null,
    val imeAction: ImeAction = ImeAction.Default,
)

internal data class CredentialRule(val hint: String? = null, val error: String? = null, val valid: Boolean = true)

internal object CredentialRules {
    private const val USERNAME_HINT = "Letters, numbers, or . _ - @. Up to 80 characters."
    private const val NAME_HINT = "Up to 80 characters."
    private const val PASSWORD_HINT = "At least 12 characters."
    private const val RECOVERY_HINT = "The code you saved when creating your account."

    fun username(value: String): CredentialRule {
        val error = when {
            validAccountUsername(value) || value.isEmpty() -> null
            value.trim().length > 80 -> "Use at most 80 characters."
            else -> "Use letters, numbers, or . _ - @ only."
        }
        return CredentialRule(hint = USERNAME_HINT.takeIf { error == null }, error = error, valid = validAccountUsername(value))
    }

    fun displayName(value: String): CredentialRule {
        val bytes = value.trim().encodeToByteArray().size
        val error = "Use at most 80 characters.".takeIf { bytes > 80 }
        return CredentialRule(hint = NAME_HINT.takeIf { error == null }, error = error,
            valid = value.trim().isNotEmpty() && bytes <= 80)
    }

    fun password(value: String): CredentialRule {
        val bytes = value.encodeToByteArray().size
        val error = when {
            value.isEmpty() -> null
            bytes < 12 -> "Use at least 12 characters."
            bytes > 256 -> "Use at most 256 characters."
            else -> null
        }
        return CredentialRule(hint = PASSWORD_HINT.takeIf { error == null }, error = error, valid = validNewPassword(value))
    }

    fun recoveryCode(value: String): CredentialRule {
        val error = "Use at most 256 characters.".takeIf { value.length > 256 }
        return CredentialRule(hint = RECOVERY_HINT.takeIf { error == null }, error = error,
            valid = value.trim().isNotEmpty() && value.length <= 256)
    }

    fun confirmation(password: String, confirmation: String): CredentialRule {
        val mismatch = confirmation.isNotEmpty() && confirmation != password
        return CredentialRule(error = "Passwords do not match.".takeIf { mismatch },
            valid = confirmation.isNotEmpty() && confirmation == password)
    }
}

internal object CredentialFields {
    fun signIn(username: String, password: String) = listOf(
        CredentialField("username", "Username", autocompleteUsername, secret = false, value = username, imeAction = ImeAction.Next),
        CredentialField("password", "Password", autocompleteCurrentPassword, secret = true, value = password, imeAction = ImeAction.Go),
    )

    fun register(username: String, name: String, password: String, confirmation: String): List<CredentialField> {
        val user = CredentialRules.username(username)
        val display = CredentialRules.displayName(name)
        val secret = CredentialRules.password(password)
        val confirm = CredentialRules.confirmation(password, confirmation)
        return listOf(
            CredentialField("username", "Username", autocompleteUsername, secret = false, value = username, errorText = user.error, hintBelow = user.hint),
            CredentialField("name", "Display name", autocompleteName, secret = false, value = name, errorText = display.error, hintBelow = display.hint),
            CredentialField("new_password", "Password", autocompleteNewPassword, secret = true, value = password, errorText = secret.error, hintBelow = secret.hint),
            CredentialField("new_password_confirmation", "Confirm password", autocompleteNewPassword, secret = true, value = confirmation, errorText = confirm.error),
        )
    }

    fun recover(username: String, code: String, password: String, confirmation: String): List<CredentialField> {
        val user = CredentialRules.username(username)
        val recovery = CredentialRules.recoveryCode(code)
        val secret = CredentialRules.password(password)
        val confirm = CredentialRules.confirmation(password, confirmation)
        return listOf(
            CredentialField("username", "Username", autocompleteUsername, secret = false, value = username, errorText = user.error, hintBelow = user.hint),
            CredentialField("recovery_code", "Recovery code", autocompleteOff, secret = true, value = code, errorText = recovery.error, hintBelow = recovery.hint),
            CredentialField("new_password", "New password", autocompleteNewPassword, secret = true, value = password, errorText = secret.error, hintBelow = secret.hint),
            CredentialField("new_password_confirmation", "Confirm password", autocompleteNewPassword, secret = true, value = confirmation, errorText = confirm.error),
        )
    }

    fun changePassword(current: String, currentError: String?, next: String, confirmation: String, confirmError: String?) = listOf(
        CredentialField("current_password", "Current password", autocompleteCurrentPassword, secret = true, value = current, errorText = currentError),
        CredentialField("new_password", "New password", autocompleteNewPassword, secret = true, value = next, hintBelow = "At least 12 characters."),
        CredentialField("new_password_confirmation", "Confirm new password", autocompleteNewPassword, secret = true, value = confirmation, errorText = confirmError),
    )
}