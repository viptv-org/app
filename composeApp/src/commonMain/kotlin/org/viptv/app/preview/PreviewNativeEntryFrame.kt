package org.viptv.app.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.features.entry.DesktopSignInBackdrop
import org.viptv.app.features.entry.SignInBackBar

@Composable
internal fun PreviewNativeEntryFrame(desktop: Boolean, back: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (desktop) DesktopSignInBackdrop()
        val frame = if (desktop) Modifier.fillMaxSize() else Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        Column(frame, horizontalAlignment = if (desktop) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = if (desktop) Arrangement.Center else Arrangement.Top) {
            if (!desktop) SignInBackBar(back)
            content()
        }
    }
}
