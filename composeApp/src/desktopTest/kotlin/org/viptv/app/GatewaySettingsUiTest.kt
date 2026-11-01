package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.GatewaySnapshot
import org.viptv.app.features.settings.GatewaySettings
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class GatewaySettingsUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private var clock = 0L
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private suspend fun frames(scene: ImageComposeScene) = repeat(8) { scene.render(clock++ * 16_666_667L).close(); delay(4) }

    private fun scene(secretStorageConfigured: Boolean) = ImageComposeScene(800, 900, Density(1f)) {
        ViptvTheme(false) {
            GatewaySettings(GatewaySnapshot(emptyList(), secret_storage_configured = secretStorageConfigured),
                emptyMap(), emptyMap(), false, null, null, null, null,
                {}, {}, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {})
        }
    }

    @Test fun missingSecretStorageDisablesRegistrationAndExplainsWhy() = runBlocking {
        val scene = scene(secretStorageConfigured = false)
        try {
            frames(scene)
            val all = nodes(scene)
            assertTrue(all.any { text(it).contains("encryption keyring") }, "the page explains why registration is unavailable")
            val register = all.firstOrNull { text(it) == "Register gateway" }
            assertNotNull(register, "the register action is present")
            assertTrue(register.config.contains(SemanticsProperties.Disabled), "registration is disabled without secret storage")
        } finally { scene.close() }
    }

    @Test fun configuredSecretStorageEnablesRegistration() = runBlocking {
        val scene = scene(secretStorageConfigured = true)
        try {
            frames(scene)
            val register = nodes(scene).firstOrNull { text(it) == "Register gateway" }
            assertNotNull(register, "the register action is present")
            assertFalse(register.config.contains(SemanticsProperties.Disabled), "registration is enabled with secret storage")
        } finally { scene.close() }
    }
}