package org.viptv.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineWireCopyTest {
    @Test
    fun matchesCoresGeneratedEngineWire() {
        val appCopy = File("src/commonMain/kotlin/org/viptv/engine/wire/EngineWire.kt")
        val coreCopy = File("../native/core/generated/engine/EngineWire.kt")
        assertTrue(appCopy.isFile, "run from the composeApp module: ${appCopy.absolutePath}")
        assertTrue(coreCopy.isFile, "native/core submodule not checked out: ${coreCopy.absolutePath}")

        val app = appCopy.readLines()
            .filterNot { it == "@Immutable" || it == "import androidx.compose.runtime.Immutable" }
        val core = coreCopy.readLines()
        assertEquals(core, app, "EngineWire copy drifted from native/core/generated/engine/EngineWire.kt")
    }
}