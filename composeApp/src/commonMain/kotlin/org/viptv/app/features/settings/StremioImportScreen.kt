package org.viptv.app.features.settings

import org.viptv.app.components.AppTextField
import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.viptv.app.connection.epochMillis
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.TextButton
import org.viptv.app.components.IconButton

private val contentWidth = 760.dp

private class Entry(val key: String, val content: @Composable () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StremioImportScreen(session: StremioImportSession?, profileName: String?, compact: Boolean = false,
    back: (() -> Unit)? = null, manageAddons: (() -> Unit)? = null) {
    val state = session?.state?.collectAsState()?.value
    var email by remember(session) { mutableStateOf("") }
    var password by remember(session) { mutableStateOf("") }
    var reveal by remember(session) { mutableStateOf(false) }
    val query = remember(session) { StremioItemQuery() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val heading = remember { FocusRequester() }
    val emailField = remember { FocusRequester() }
    val passwordField = remember { FocusRequester() }
    val firstFailed = remember { FocusRequester() }
    DisposableEffect(session) { onDispose { password = "" } }
    LaunchedEffect(state?.error) { if (state?.error != null) password = "" }
    LaunchedEffect(state?.resets) {
        password = ""; reveal = false
        if ((state?.resets ?: 0) > 0) { email = ""; query.search = ""; query.filter = "all"; query.page = 0
            list.toTop(); runCatching { emailField.requestFocus() } }
    }
    LaunchedEffect(state?.stage, state?.connected) {
        if (state != null && (state.stage != StremioImportStage.Connect || state.connected)) { list.toTop(); runCatching { heading.requestFocus() } }
    }
    val name = profileName ?: "this profile"
    val entries = mutableListOf<Entry>()
    fun add(key: String, content: @Composable () -> Unit) { entries += Entry(key, content) }
    var bar: (@Composable () -> Unit)? = null

    back?.let { add("back") { TextButton(it) { Text("Back to settings") } } }
    add("title") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Import from Stremio", fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.48).sp)
            Text("Import into: $name", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Preview first. Nothing is saved until you confirm the import.", fontSize = 14.sp, color = Palette.secondary)
        }
    }
    if (session == null || state == null || state.restricted) {
        add("restricted") { Text("Switch to an unrestricted profile to import.", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp) }
    } else {
        val busy = state.busy != null
        val completion = state.completion
        val review = state.review
        val addons = state.addons
        if (completion == null) add("step") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StepBar(state.stage.step)
                Text("Step ${state.stage.step} of 4: ${state.stage.title}", fontSize = 13.sp, color = Palette.secondary)
                val active = review ?: addons
                if (active != null && state.stage != StremioImportStage.Connect && !state.expired) ExpiryHint(active.expiresAt)
            }
        }
        when {
            completion != null -> {
                add("heading") { StageHeading(heading, "Import complete") }
                add("outcome") {
                    Text(if (completion.alreadyCompleted) "This import was already completed." else "The confirmed changes were saved to this profile.",
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
                }
                state.details?.let { details -> add("details") { TitleDetailsStatus(details) } }
                add("summary") { StremioSummary(completion.summary, stremioCompletionLabels) }
                add("extras") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        completion.addonsAdded?.let { Text("Add-ons added to your account: $it", fontSize = 14.sp) }
                        completion.excludedItems?.let { Text("Review items excluded: $it", fontSize = 14.sp) }
                    }
                }
                add("actions") { ImportActions { OutlinedButton(session::startAnother) { Text("Start another preview") } } }
            }
            state.stage == StremioImportStage.Addons && addons != null -> {
                val ordinals = remember(addons) { stremioDuplicateOrdinals(addons.addons) }
                val failedFirst = addons.addons.firstOrNull { it.id in state.failedAddons }?.id
                val available = addons.addons.count { it.status != "unavailable" }
                val newOnes = addons.addons.count { it.status == "add" && it.id in state.selectedAddons }
                add("heading") { StageHeading(heading, "Choose add-ons") }
                add("intro") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Do you want to bring these add-ons into VIPTV? Uncheck any you do not want.", fontSize = 14.sp)
                        ImportNote("Metadata add-ons can help verify titles and episode identities. Stream-only add-ons do not supply episode metadata.")
                    }
                }
                if (addons.addons.isEmpty()) add("empty") {
                    Text("No Stremio add-ons were found. Your existing account add-ons can still assist.",
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
                } else add("selection") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${state.selectedAddons.size} of $available available add-ons selected · $newOnes new to your account",
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
                        ImportActions {
                            OutlinedButton(session::selectAllAddons, enabled = !busy && !state.expired) { Text("Select all") }
                            OutlinedButton(session::clearAddons, enabled = !busy && !state.expired) { Text("Clear all") }
                        }
                    }
                }
                if (state.failedAddons.isNotEmpty()) add("failed-summary") {
                    val count = state.failedAddons.size
                    Text(if (count == 1) "1 add-on couldn't be checked. Uncheck it, or continue without add-ons."
                        else "$count add-ons couldn't be checked. Uncheck them, or continue without add-ons.",
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.danger)
                }
                addons.addons.forEach { addon ->
                    add("addon:${addon.id}") {
                        StremioAddonRow(addon, addon.id in state.selectedAddons, addon.id in state.failedAddons, busy || state.expired,
                            ordinals[addon.id], if (addon.id == failedFirst) Modifier.focusRequester(firstFailed) else Modifier,
                            state.failedReasons[addon.id]) {
                            session.toggleAddon(addon.id, it)
                        }
                    }
                }
                add("addon-notes") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ImportNote("Selected new add-ons are account-wide, not limited to this profile. Their private configuration is encrypted and is never shown here. Nothing is added until final confirmation.")
                        ImportNote("Continuing without add-ons only limits this import; add-ons already in your account are never removed.")
                    }
                }
                val failedNames = addons.addons.filter { it.id in state.failedAddons }
                    .joinToString(", ") { it.name + ordinals[it.id]?.let { (n, of) -> " ($n of $of)" }.orEmpty() }
                bar = {
                    ImportFooter(state, if (failedNames.isEmpty()) emptyList() else listOf("Couldn't check: $failedNames." to true)) {
                        OutlinedButton(session::back, enabled = !busy && !state.selectionLocked) { Text("Back") }
                        if (state.failedAddons.isNotEmpty()) OutlinedButton(session::uncheckFailedAddons, enabled = !busy && !state.expired) { Text("Uncheck failed add-ons") }
                        if (state.busy == StremioImportBusy.Review) OutlinedButton(session::cancel) { Text("Cancel") }
                        OutlinedButton(session::continueWithoutAddons, enabled = !busy && !state.expired) { Text("Continue without add-ons") }
                        Button(session::reviewItems, enabled = !busy && !state.expired) { Text("Next: Review items") }
                    }
                }
                LaunchedEffect(state.failedReports) {
                    val index = entries.indexOfFirst { it.key == "addon:$failedFirst" }
                    if (state.failedReports > 0 && failedFirst != null && index >= 0) {
                        afterLayout(); list.animateScrollToItem(index); afterLayout(); runCatching { firstFailed.requestFocus() }
                    }
                }
            }
            state.stage == StremioImportStage.Items && review != null -> {
                val disabled = busy || state.expired || state.selectionLocked
                val filtered = remember(review.items, query.search, query.filter, state.excluded) { query.filtered(review.items, state.excluded) }
                val pages = maxOf(1, (filtered.size + stremioPageSize - 1) / stremioPageSize)
                val current = minOf(query.page, pages - 1)
                add("heading") { StageHeading(heading, "Review items") }
                add("summary") { StremioSummary(state.chosenCounts.orEmpty(), stremioPreviewLabels) }
                add("addons-to-add") { Text("New add-ons to register for your account: ${review.addonsToAdd}", fontSize = 14.sp) }
                add("review-note") {
                    ImportNote("Newer or equal VIPTV activity and manual corrections are preserved. Verified watched marks are added without inventing episode watch dates or changing active resumes. Completion-only entries do not populate Continue Watching. Likes/loves are not imported.")
                }
                add("controls") { StremioItemControls(query, review.items, filtered, current, state.excluded, disabled, session::setItemsIncluded) }
                val rowsStart = entries.size
                filtered.subList(current * stremioPageSize, minOf(filtered.size, (current + 1) * stremioPageSize)).forEach { item ->
                    add("item:${item.id}") {
                        StremioReviewRow(item, item.selectable && item.id !in state.excluded, disabled) { session.setItemsIncluded(setOf(item.id), it) }
                    }
                }
                if (pages > 1) add("pager") {
                    StremioPager(current, pages, filtered.size, disabled) { query.page = it; scope.launch { list.scrollToItem(rowsStart) } }
                }
                add("exclusion-note") {
                    ImportNote("Excluding an item only limits this import. It never removes anything from Stremio or VIPTV. Entries needing review remain unchanged.")
                }
                bar = {
                    ImportFooter(state, if (!state.hasScope && !state.expired) listOf("Include at least one item, or go back and choose a new add-on, to continue." to false) else emptyList()) {
                        OutlinedButton(session::back, enabled = !busy && !state.selectionLocked) { Text("Back") }
                        OutlinedButton(session::cancel, enabled = !busy) { Text(if (state.expired) "Preview again" else "Cancel") }
                        Button(session::toConfirm, enabled = !busy && !state.expired && state.hasScope) { Text("Next: Confirm import") }
                    }
                }
            }
            state.stage == StremioImportStage.Confirm && review != null -> {
                val chosen = addons?.addons?.filter { it.id in state.selectedAddons }.orEmpty()
                add("heading") { StageHeading(heading, "Confirm import") }
                add("destination") { Text("Review your chosen import into $name.", fontSize = 14.sp) }
                add("summary") { StremioSummary(state.chosenCounts.orEmpty(), stremioPreviewLabels) }
                add("recap") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Selected review items: ${state.included}. New account-wide add-ons: ${review.addonsToAdd}.", fontSize = 14.sp)
                        if (chosen.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Chosen add-ons:", fontSize = 14.sp)
                            chosen.forEach { Text("• ${it.name}" + if (it.status == "existing") " (already configured)" else "", fontSize = 14.sp) }
                        } else ImportNote("No add-ons chosen. Add-ons already in your account stay as they are.")
                        if (review.addonsToAdd > 0) Text("Selected new add-ons are added to your account only when you confirm.", fontSize = 14.sp)
                        if (state.selectionLocked) ImportNote("This selection is locked while its result is being confirmed. Retrying uses the same items.")
                    }
                }
                add("confirm") {
                    StremioCheckRow("I confirm this import is for $name.", state.confirmed, !busy && !state.expired && state.hasScope, change = session::setConfirmed)
                }
                bar = {
                    ImportFooter(state) {
                        OutlinedButton(session::back, enabled = !busy && !state.selectionLocked) { Text("Back") }
                        OutlinedButton(session::cancel, enabled = !busy && !state.selectionLocked) { Text(if (state.expired) "Preview again" else "Cancel") }
                        Button(session::apply, enabled = state.confirmed && state.hasScope && !state.expired && !busy) { Text("Confirm import") }
                    }
                }
            }
            state.connected -> {
                add("heading") { StageHeading(heading, "Connect Stremio") }
                add("draft") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Stremio is connected for this preview. Your add-on and item choices are still saved in this draft.", fontSize = 14.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Stremio email", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(state.email, fontSize = 14.sp)
                            Text("Source choices", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull("My List".takeIf { state.library }, "Watch history and resume".takeIf { state.progress })
                                .joinToString(" · ").ifEmpty { "Add-ons only" }, fontSize = 14.sp)
                        }
                        ImportNote("Your password was cleared after connecting. Changing the connection starts a new preview.")
                    }
                }
                add("actions") {
                    ImportFooter(state) {
                        Button(session::resume, enabled = !state.expired && !busy) { Text(if (addons != null) "Next: Choose add-ons" else "Next: Review items") }
                        OutlinedButton(session::changeConnection, enabled = !busy) { Text("Change connection") }
                    }
                }
            }
            else -> {
                val submit = { if (!busy && email.isNotBlank() && password.isNotEmpty()) {
                    val secret = password; password = ""; reveal = false; session.connect(email, secret) } }
                add("heading") { StageHeading(heading, "Connect Stremio") }
                add("explain") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("VIPTV reads your Stremio account once for a read-only preview. You choose add-ons and items before anything is saved.", fontSize = 14.sp)
                        ImportNote("Review add-ons, verify metadata, then choose exactly which items to import. Viewing data goes to $name. Selected add-ons are shared across your account's profiles.")
                        ImportNote("Imported: titles saved to My List, movie and episode resume points, and watched episodes that can be verified against metadata. Compatible metadata add-ons help verify titles, episodes and watched flags. Watched entries without dates are labelled as imported and do not change Continue Watching.")
                        ImportNote("Left for review: entries whose title or episode can't be verified. Likes/loves are not imported.")
                    }
                }
                add("email") {
                    AppTextField(email, { email = it.take(254) }, Modifier.fillMaxWidth().focusRequester(emailField), enabled = !busy, label = "Stremio email",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { runCatching { passwordField.requestFocus() } }))
                }
                add("password") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppTextField(password, { password = it.take(1024) }, Modifier.fillMaxWidth().focusRequester(passwordField), enabled = !busy, label = "Stremio password",
                            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            trailingIcon = { IconButton({ reveal = !reveal }, enabled = !busy) {
                                Icon(if (reveal) Lucide.EyeOff else Lucide.Eye, if (reveal) "Hide password" else "Show password", Modifier.size(20.dp))
                            } })
                        ImportNote("Your password is used for this request only and is not saved by VIPTV.")
                    }
                }
                add("sources") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StremioCheckRow("My List", state.library, !busy, supporting = "Titles saved in your Stremio library", change = session::setLibrary)
                        StremioCheckRow("Watch history and resume", state.progress, !busy, supporting = "Resume points and watched episodes", change = session::setProgress)
                        if (!state.library && !state.progress) ImportNote("With both off, only add-ons are reviewed.")
                    }
                }
                add("actions") {
                    ImportFooter(state) {
                        Button(submit, enabled = !busy && email.isNotBlank() && password.isNotEmpty()) { Text("Next: Choose add-ons") }
                        if (busy) OutlinedButton(session::cancel) { Text("Cancel") }
                        if (manageAddons != null && !busy) OutlinedButton(manageAddons) { Text("Manage existing add-ons") }
                    }
                }
            }
        }
    }

    val padding = if (compact) 16.dp else 0.dp
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), list,
            contentPadding = PaddingValues(start = padding, end = padding, top = padding, bottom = if (compact) 16.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(entries, key = { it.key }) { entry -> Box(Modifier.widthIn(max = contentWidth).fillMaxWidth()) { entry.content() } }
        }
        bar?.let { content ->
            HorizontalDivider(color = Palette.text.copy(alpha = .08f))
            Box(Modifier.fillMaxWidth().background(Palette.background).padding(horizontal = padding, vertical = 12.dp)) {
                Box(Modifier.widthIn(max = contentWidth).fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable
private fun StageHeading(focus: FocusRequester, text: String) = Text(text, Modifier.focusRequester(focus).focusable().semantics { heading() },
    fontSize = 18.sp, fontWeight = FontWeight.Bold)

@Composable
private fun ImportNote(text: String) = Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = Palette.secondary)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportActions(content: @Composable FlowRowScope.() -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportFooter(state: StremioImportState, hints: List<Pair<String, Boolean>> = emptyList(), content: @Composable FlowRowScope.() -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = Palette.danger, fontSize = 14.sp) }
        state.notice?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp) }
        hints.forEach { (text, alert) -> Text(text, fontSize = 13.sp, color = if (alert) Palette.danger else Palette.secondary) }
        state.busy?.let { BusyStatus(it) }
        ImportActions(content)
    }

/** Lazy entries attach during layout, after effects start; focus them once they exist. */
private suspend fun afterLayout() = repeat(2) { withFrameNanos { } }

/** Scrolls on the next frame, never inside the composition that changed the stage. */
private suspend fun androidx.compose.foundation.lazy.LazyListState.toTop() {
    afterLayout()
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) { scrollToItem(0); afterLayout() }
}

@Composable
private fun StepBar(step: Int) {
    Row(Modifier.widthIn(max = 320.dp).fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { index ->
            Box(Modifier.weight(1f).height(4.dp).background(if (index < step) Palette.accent else Palette.text.copy(alpha = .14f), RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun BusyStatus(busy: StremioImportBusy) {
    var elapsed by remember(busy) { mutableStateOf(0) }
    LaunchedEffect(busy) { while (true) { delay(1_000); elapsed++ } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
        Text(busy.label, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp)
        Text(busy.expectation, fontSize = 13.sp, color = Palette.secondary)
        if (elapsed >= 10) Text("Still working · ${elapsed} s", fontSize = 13.sp, color = Palette.secondary)
    }
}

@Composable
private fun TitleDetailsStatus(details: StremioTitleDetails) {
    val running = details.phase == StremioDetailsPhase.Running
    val counted = details.total > 0
    val partly = details.phase == StremioDetailsPhase.Ready && details.filled != null && details.filled < details.total
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(when (details.phase) {
            StremioDetailsPhase.Running -> "Getting title details…"
            StremioDetailsPhase.Ready -> if (partly) "Finished getting title details." else "Title details are ready."
            StremioDetailsPhase.Background -> "Title details are still being added in the background."
        }, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (running) {
            val share = if (counted) details.done.toFloat() / details.total else 0f
            val shown by animateFloatAsState(share, tween(1_800, easing = LinearEasing), label = "title details")
            if (counted && details.done > 0) LinearProgressIndicator({ shown },
                Modifier.fillMaxWidth().semantics { stateDescription = "${details.done} of ${details.total} titles" }, color = Palette.accent)
            else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.accent)
            if (counted) Text("${details.done} of ${details.total} titles", fontSize = 13.sp, color = Palette.secondary)
        }
        ImportNote(when (details.phase) {
            StremioDetailsPhase.Running -> "You can leave this screen. Details keep filling in the background."
            StremioDetailsPhase.Ready -> if (partly) "Details were found for ${details.filled} of ${details.total} titles. The others keep their name and poster."
                else "Imported titles now show their description, artwork and ratings."
            StremioDetailsPhase.Background -> "My List updates as they arrive."
        })
    }
}

@Composable
private fun ExpiryHint(expiresAt: Long) {
    var now by remember { mutableStateOf(epochMillis()) }
    LaunchedEffect(expiresAt) { while (true) { delay(15_000); now = epochMillis() } }
    val minutes = (expiresAt * 1000 - now) / 60_000
    Text(if (minutes >= 1) "This preview expires in about $minutes min unless you confirm it." else "This preview expires in less than a minute.",
        fontSize = 13.sp, color = Palette.secondary)
}
