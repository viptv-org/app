package org.viptv.app.components

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

// Marks chrome that paints its own background over the page, so a render test can tell it from text that really collides.
internal val OverlaysContentKey = SemanticsPropertyKey<Unit>("OverlaysContent")
internal var SemanticsPropertyReceiver.overlaysContent by OverlaysContentKey
