package org.viptv.app

import java.io.File
import kotlin.test.*

class ProfileLimitTest {
    private val root = File("src/commonMain/kotlin/org/viptv/app")
    private fun source(name: String) = File(root, name).readText()

    private val pickers = listOf(
        "features/entry/MobileProfiles.kt",
        "features/entry/DesktopProfiles.kt",
        "features/entry/TvProfiles.kt",
        "features/entry/WideProfiles.kt").filter { File(root, it).exists() }

    @Test fun theLimitIsOneConstant() {
        assertTrue(source("features/entry/ProfileLimit.kt").contains("internal const val ProfileLimit = 12"),
            "one shared constant owns the profile limit")
        assertTrue(pickers.isNotEmpty(), "run from the composeApp module: ${root.absolutePath}")
        for (picker in pickers) {
            assertFalse(Regex("""size < 12\b""").containsMatchIn(source(picker)), "$picker reads the shared profile limit")
        }
    }

    @Test fun everyPickerDrawsTheAddTileAndOnlyTheLimitDisablesIt() {
        for (picker in pickers) {
            val text = source(picker)
            assertTrue(text.contains("dashedOutline("), "$picker draws the add tile dashed")
            assertTrue(Regex("""size < ProfileLimit""").containsMatchIn(text), "$picker disables the add tile at the limit")
            assertFalse(Regex("""if \(local && profiles\.size <""").containsMatchIn(text),
                "$picker keeps the add tile drawn for every household that may manage profiles")
        }
    }
}