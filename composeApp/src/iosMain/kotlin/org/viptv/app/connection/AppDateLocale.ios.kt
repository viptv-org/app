package org.viptv.app.connection

import platform.Foundation.NSLocale

internal val appDateLocale: NSLocale get() = NSLocale(localeIdentifier = APP_DATE_LOCALE.replace('-', '_'))