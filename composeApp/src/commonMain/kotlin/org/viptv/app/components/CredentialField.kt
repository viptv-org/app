package org.viptv.app.components

import androidx.compose.ui.text.input.ImeAction

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

internal object CredentialFields {
    fun signIn(username: String, password: String) = listOf(
        CredentialField("username", "Username", autocompleteUsername, secret = false, value = username, imeAction = ImeAction.Next),
        CredentialField("password", "Password", autocompleteCurrentPassword, secret = true, value = password, imeAction = ImeAction.Go),
    )

    fun register(username: String, name: String, password: String, confirmation: String, confirmError: String?) = listOf(
        CredentialField("username", "Username", autocompleteUsername, secret = false, value = username),
        CredentialField("name", "Display name", autocompleteName, secret = false, value = name),
        CredentialField("new_password", "Password", autocompleteNewPassword, secret = true, value = password),
        CredentialField("new_password_confirmation", "Confirm password", autocompleteNewPassword, secret = true, value = confirmation, errorText = confirmError),
    )

    fun recover(username: String, code: String, password: String, confirmation: String, confirmError: String?) = listOf(
        CredentialField("username", "Username", autocompleteUsername, secret = false, value = username),
        CredentialField("recovery_code", "Recovery code", autocompleteOff, secret = true, value = code),
        CredentialField("new_password", "New password", autocompleteNewPassword, secret = true, value = password),
        CredentialField("new_password_confirmation", "Confirm password", autocompleteNewPassword, secret = true, value = confirmation, errorText = confirmError),
    )

    fun changePassword(current: String, currentError: String?, next: String, confirmation: String, confirmError: String?) = listOf(
        CredentialField("current_password", "Current password", autocompleteCurrentPassword, secret = true, value = current, errorText = currentError),
        CredentialField("new_password", "New password", autocompleteNewPassword, secret = true, value = next, hintBelow = "At least 12 characters."),
        CredentialField("new_password_confirmation", "Confirm new password", autocompleteNewPassword, secret = true, value = confirmation, errorText = confirmError),
    )
}