package org.viptv.app.features.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.state.*
import org.viptv.app.theme.Palette

/**
 * IMP-001 guided Stremio import. The password lives only in this composition's
 * field (never saveable state) and is handed to one preview request, then cleared.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StremioImportScreen(session: StremioImportSession?, profileName: String?, compact: Boolean = false, back: (() -> Unit)? = null) {
    val state = session?.state?.collectAsState()?.value
    var email by remember(session) { mutableStateOf("") }
    var password by remember(session) { mutableStateOf("") }
    var reveal by remember(session) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val heading = remember { FocusRequester() }
    val emailField = remember { FocusRequester() }
    DisposableEffect(session) { onDispose { password = "" } }
    LaunchedEffect(state?.error) { if (state?.error != null) password = "" }
    LaunchedEffect(state?.resets) {
        password = ""; reveal = false
        if ((state?.resets ?: 0) > 0) { email = ""; scroll.scrollTo(0); runCatching { emailField.requestFocus() } }
    }
    LaunchedEffect(state?.stage, state?.connected) {
        if (state != null && (state.stage != StremioImportStage.Connect || state.connected)) { scroll.scrollTo(0); runCatching { heading.requestFocus() } }
    }
    val name = profileName ?: "this profile"
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(if (compact) PaddingValues(16.dp) else PaddingValues(bottom = 32.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        back?.let { TextButton(it) { Text("Back to settings") } }
        Text("Import from Stremio", fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.48).sp)
        Text("Import into: $name", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Preview first. Nothing is saved until you confirm the import.", fontSize = 14.sp, color = Palette.secondary)
        state?.error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = Palette.danger, fontSize = 14.sp) }
        if (session == null || state == null || state.restricted) {
            Text("Switch to an unrestricted profile to import.", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
            return@Column
        }
        val busy = state.busy != null
        if (state.completion == null) Text("Step ${state.stage.step} of 4: ${state.stage.title}", fontSize = 13.sp, color = Palette.secondary)
        @Composable fun Heading(text: String) = Text(text, Modifier.focusRequester(heading).focusable().semantics { heading() },
            fontSize = 18.sp, fontWeight = FontWeight.Bold)
        @Composable fun Busy() = state.busy?.let { Text(it.label, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp) }
        @Composable fun Actions(content: @Composable FlowRowScope.() -> Unit) =
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        val completion = state.completion
        val review = state.review
        val addons = state.addons
        when {
            completion != null -> {
                Heading("Import complete")
                Text(if (completion.alreadyCompleted) "This import was already completed." else "The confirmed changes were saved to this profile.",
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
                StremioSummary(completion.summary, stremioCompletionLabels)
                completion.addonsAdded?.let { Text("Add-ons added to your account: $it", fontSize = 14.sp) }
                completion.excludedItems?.let { Text("Review items excluded: $it", fontSize = 14.sp) }
                Actions { OutlinedButton(session::startAnother) { Text("Start another preview") } }
            }
            state.stage == StremioImportStage.Addons && addons != null -> {
                Heading("Choose add-ons")
                Text(if (state.addonsEditable) "Do you want to bring these add-ons into VIPTV? Uncheck any you do not want."
                    else "These add-ons were found in Stremio.", fontSize = 14.sp)
                StremioAddonReview(addons.addons, state.selectedAddons, state.failedAddons, state.addonsEditable, busy || state.expired, session::toggleAddon)
                if (state.addonsEditable) Actions {
                    OutlinedButton(session::selectAllAddons, enabled = !busy && !state.expired) { Text("Select all") }
                    OutlinedButton(session::clearAddons, enabled = !busy && !state.expired) { Text("Clear all") }
                }
                Busy()
                Actions {
                    OutlinedButton(session::back, enabled = !busy && !state.selectionLocked) { Text("Back") }
                    if (state.addonsEditable) OutlinedButton(session::continueWithoutAddons, enabled = !busy && !state.expired) { Text("Continue without add-ons") }
                    Button(session::reviewItems, enabled = !busy && !state.expired) { Text("Next: Review items") }
                }
            }
            state.stage == StremioImportStage.Items && review != null -> {
                Heading("Review items")
                StremioSummary(state.chosenCounts.orEmpty(), stremioPreviewLabels)
                if (state.addonsEditable) Text("New add-ons to register for your account: ${review.addonsToAdd}", fontSize = 14.sp)
                StremioItemReview(review.items, state.excluded, busy || state.expired || state.selectionLocked, session::setItemsIncluded)
                Text("Newer or equal VIPTV activity and manual corrections are preserved. Verified watched marks are added without inventing episode watch dates or changing active resumes. Completion-only entries do not populate Continue Watching. Likes/loves are not imported.",
                    fontSize = 13.sp, color = Palette.secondary)
                Busy()
                Actions {
                    OutlinedButton(session::back, enabled = !busy && !state.selectionLocked) { Text("Back") }
                    OutlinedButton(session::cancel, enabled = !busy) { Text(if (state.expired) "Preview again" else "Cancel") }
                    Button(session::toConfirm, enabled = !busy && !state.expired && state.hasScope) { Text("Next: Confirm import") }
                }
            }
            state.stage == StremioImportStage.Confirm && review != null -> {
                Heading("Confirm import")
                Text("Review your chosen import into $name.", fontSize = 14.sp)
                StremioSummary(state.chosenCounts.orEmpty(), stremioPreviewLabels)
                Text("Selected review items: ${state.included}." + if (state.addonsEditable) " New account-wide add-ons: ${review.addonsToAdd}." else "", fontSize = 14.sp)
                val chosen = addons?.addons?.filter { it.id in state.selectedAddons }.orEmpty()
                if (chosen.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Chosen add-ons:", fontSize = 14.sp)
                    chosen.forEach { Text("• ${it.name}" + if (it.status == "existing") " (already configured)" else "", fontSize = 14.sp) }
                }
                if (review.addonsToAdd > 0) Text("Selected new add-ons are added to your account only when you confirm.", fontSize = 14.sp)
                if (state.selectionLocked) Text("This selection is locked while its result is being confirmed. Retrying uses the same items.",
                    fontSize = 13.sp, color = Palette.secondary)
                StremioCheckRow("I confirm this import is for $name.", state.confirmed, !busy && !state.expired && state.hasScope, change = session::setConfirmed)
                Busy()
                Actions {
                    OutlinedButton(session::back, enabled = !busy && !state.selectionLocked) { Text("Back") }
                    OutlinedButton(session::cancel, enabled = !busy && !state.selectionLocked) { Text(if (state.expired) "Preview again" else "Cancel") }
                    Button(session::apply, enabled = state.confirmed && state.hasScope && !state.expired && !busy) { Text("Confirm import") }
                }
            }
            state.connected -> {
                Heading("Connect Stremio")
                Text("Stremio is connected for this preview. Your add-on and item choices are still saved in this draft.", fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Stremio email", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(state.email, fontSize = 14.sp)
                    Text("Source choices", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull("My List".takeIf { state.library }, "Watch history and resume".takeIf { state.progress })
                        .joinToString(" · ").ifEmpty { "Add-ons only" }, fontSize = 14.sp)
                }
                Text("Your password was cleared after connecting. Changing the connection starts a new preview.", fontSize = 13.sp, color = Palette.secondary)
                Actions {
                    Button(session::resume, enabled = !state.expired && !busy) { Text(if (addons != null) "Next: Choose add-ons" else "Next: Review items") }
                    OutlinedButton(session::changeConnection, enabled = !busy) { Text("Change connection") }
                }
            }
            else -> {
                Heading("Connect Stremio")
                OutlinedTextField(email, { email = it.take(254) }, Modifier.fillMaxWidth().focusRequester(emailField), enabled = !busy,
                    singleLine = true, label = { Text("Stremio email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false))
                OutlinedTextField(password, { password = it.take(1024) }, Modifier.fillMaxWidth(), enabled = !busy,
                    singleLine = true, label = { Text("Stremio password") },
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = { IconButton({ reveal = !reveal }, enabled = !busy) {
                        Icon(if (reveal) Lucide.EyeOff else Lucide.Eye, if (reveal) "Hide password" else "Show password", Modifier.size(20.dp))
                    } })
                StremioCheckRow("My List", state.library, !busy, change = session::setLibrary)
                StremioCheckRow("Watch history and resume", state.progress, !busy, change = session::setProgress)
                Busy()
                Actions {
                    Button({ val secret = password; password = ""; reveal = false; session.connect(email, secret) },
                        enabled = !busy && email.isNotBlank() && password.isNotEmpty()) { Text("Next: Choose add-ons") }
                    if (busy) OutlinedButton(session::cancel) { Text("Cancel") }
                }
                Text("Compatible metadata add-ons help verify titles, episodes and watched flags. Watched entries without dates are labelled as imported and do not change Continue Watching. Unverified entries stay for review. Your password is used for this request only and is not saved by VIPTV.",
                    fontSize = 13.sp, color = Palette.secondary)
            }
        }
    }
}
