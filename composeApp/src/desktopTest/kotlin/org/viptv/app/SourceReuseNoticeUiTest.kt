package org.viptv.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.sources.SourceReuseNotice
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SourceReuseNoticeUiTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun theManualOverrideAppearsAfterTheVisibleFrameBudgetAndInvokesItsAction() {
        val card = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "catalog", title = "Movie", badges = emptyList())
        var chosen = 0
        val scene = ImageComposeScene(960, 640, Density(1f)) { MaterialTheme { SourceReuseNotice(card) { chosen++ } } }
        fun button() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }.firstOrNull {
            it.config.contains(SemanticsActions.OnClick) && it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "Choose source" }
        }
        fun draw(nanos: Long) { scene.render(nanos).close() }
        try {
            draw(0); draw(1_000_000); draw(2_000_000)
            assertNull(button())
            draw(600_000_000); draw(601_000_000)
            assertNull(button(), "600 ms of displayed frames do not expose the override")
            draw(800_000_000); draw(801_000_000)
            assertTrue(assertNotNull(button()).config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, chosen)
        } finally { scene.close() }
    }
}
