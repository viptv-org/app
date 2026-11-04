package org.viptv.app.connection

import java.time.Clock

// Nullable only so tests can fix "today"; production always reads the system clock.
@Volatile internal var appDateClock: Clock? = null
internal fun clock(): Clock = appDateClock ?: Clock.systemDefaultZone()