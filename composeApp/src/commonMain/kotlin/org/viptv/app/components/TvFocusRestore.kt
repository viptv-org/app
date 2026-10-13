package org.viptv.app.components

import androidx.compose.ui.focus.FocusRequester

internal class TvFocusHandle { var request: (() -> Boolean)? = null }

/** A lazy target may still be awaiting placement during a row transition. */
internal fun FocusRequester.requestPlacedFocus(): Boolean = try { requestFocus() }
catch (_: IllegalStateException) { false }
