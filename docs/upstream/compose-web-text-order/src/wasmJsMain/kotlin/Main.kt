import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import kotlin.js.js
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

private fun publish(value: String) {
    js("window.__typed = value")
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body as HTMLElement) {
        var typed by remember { mutableStateOf("") }
        // Publish Compose's own state to JS so a test can read what the field actually holds.
        LaunchedEffect(typed) { publish(typed) }
        Box(Modifier.fillMaxSize()) {
            BasicTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.padding(40.dp),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 28.sp),
                cursorBrush = SolidColor(Color.White),
            )
        }
    }
}
