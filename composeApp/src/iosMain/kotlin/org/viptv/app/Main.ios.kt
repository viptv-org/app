package org.viptv.app

import androidx.compose.ui.window.ComposeUIViewController
import org.viptv.app.state.SourceMemory

/** iOS entry: remote-first, browser-parity. The Swift shell (app/iosApp)
 *  presents this controller; local-engine parity via cinterop is a follow-up. */
fun MainViewController() = run {
    SourceMemory.useDeviceStorage()
    ComposeUIViewController { App(browser = true) }
}
