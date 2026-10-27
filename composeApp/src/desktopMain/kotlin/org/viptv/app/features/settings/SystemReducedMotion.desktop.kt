package org.viptv.app.features.settings

import coil3.PlatformContext

/** The JVM has no portable reduced-motion query; the Display setting's On still applies. */
internal actual fun systemReducedMotion(context: PlatformContext): Boolean = false
