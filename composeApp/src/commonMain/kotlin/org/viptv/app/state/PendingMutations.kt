package org.viptv.app.state

import androidx.compose.runtime.Immutable

/** What a write changes; [MutationTarget.target] names the row, toggle, dialog or track it is keyed by. */
enum class MutationKind { MyList, Preference, Addon, SourceIntegration, Xtream, Gateway, AudioTrack, Profile, Account }

@Immutable
data class MutationTarget(val kind: MutationKind, val target: String)

enum class MutationPhase { Running, Failed }

@Immutable
data class PendingMutation(val phase: MutationPhase, val failure: String? = null)

/** One entry per target, so a control draws its own pending state and a repeat press is dropped until it settles. */
@Immutable
data class PendingMutations(val entries: Map<MutationTarget, PendingMutation> = emptyMap()) {
    fun isPending(target: MutationTarget): Boolean = entries[target]?.phase == MutationPhase.Running
    fun failure(target: MutationTarget): String? = entries[target]?.takeIf { it.phase == MutationPhase.Failed }?.failure
    fun running(kind: MutationKind): Boolean = entries.any { it.key.kind == kind && it.value.phase == MutationPhase.Running }
    internal fun starting(target: MutationTarget) = copy(entries = entries + (target to PendingMutation(MutationPhase.Running)))
    internal fun failed(target: MutationTarget, message: String) = copy(entries = entries + (target to PendingMutation(MutationPhase.Failed, message)))
    internal fun settled(target: MutationTarget) = copy(entries = entries - target)
}
