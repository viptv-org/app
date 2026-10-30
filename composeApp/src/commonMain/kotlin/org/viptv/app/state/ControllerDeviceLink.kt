package org.viptv.app.state

import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

class DeviceCodeLink internal constructor(val origin: String, val code: String, val delivered: Boolean = false,
    private val received: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()) {
    val expired: Boolean get() = received.elapsedNow() > 10.minutes
    internal fun delivered() = DeviceCodeLink(origin, code, true, received)
    override fun toString() = "DeviceCodeLink(code=<redacted>)"
}

private val deviceCodeShape = Regex("[0-9]{8}")
private val beforeProfile = setOf(Screen.Connect, Screen.RecoveryCodes, Screen.Profiles, Screen.ProfilePin, Screen.ProfileEdit)

fun ApplicationController.offerDeviceCode(origin: String, code: String) {
    val checked = runCatching { org.viptv.app.connection.validatedRemoteOrigin(origin) }.getOrNull() ?: return
    if (deviceCodeShape.matches(code)) deviceLink.value = DeviceCodeLink(checked, code)
}

fun ApplicationController.deliverDeviceCode() {
    val link = deviceLink.value ?: return
    if (link.expired) { deviceLink.value = null; return }
    val current = mutable.value
    if (link.delivered || current.starting || current.screen in beforeProfile) return
    val origin = connection?.accountDashboardUrl?.trimEnd('/')
    if (current.localMode || origin == null || !origin.equals(link.origin, ignoreCase = true)) { deviceLink.value = null; return }
    deviceLink.value = link.delivered()
    updateState { current -> current.copy(settingsEntrySection = "Watch on TV") }
    navigate(Screen.Settings)
}

fun ApplicationController.consumeDeviceCode() { deviceLink.value = null }
