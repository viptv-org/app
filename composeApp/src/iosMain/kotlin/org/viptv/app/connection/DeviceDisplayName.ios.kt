package org.viptv.app.connection

import platform.UIKit.UIDevice

internal actual fun deviceDisplayName(): String = "VIPTV ${UIDevice.currentDevice.model}"
