package org.viptv.app.state

internal const val passwordChangedNotice = "Password changed. Other devices stay signed in."

fun ApplicationController.passwordChanged() {
    updateState { it.copy(passwordChangeNotice = passwordChangedNotice) }
    showSettingsPage(SettingsPage("Account"))
}
