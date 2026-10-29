package org.viptv.app.player

internal data class AudioCandidate(val id: Int, val supported: Boolean = true)

internal sealed interface RequiredAudioChoice {
    data object Unchanged : RequiredAudioChoice
    data object Unavailable : RequiredAudioChoice
    data class Select(val id: Int) : RequiredAudioChoice
}

// Provider ordinals count audio streams, including unsupported ones, before UI filtering.
internal class RequiredAudioSelection(private val ordinal: Int?) {
    private var touched = false
    private var applied: Int? = null
    private var unavailable = false
    fun userSelected() { touched = true }

    fun choose(candidates: List<AudioCandidate>, tracksKnown: Boolean = candidates.isNotEmpty()): RequiredAudioChoice {
        if (ordinal == null || touched || unavailable || !tracksKnown) return RequiredAudioChoice.Unchanged
        val target = candidates.getOrNull(ordinal)?.takeIf { it.supported }
            ?: return RequiredAudioChoice.Unavailable.also { unavailable = true }
        if (applied == target.id) return RequiredAudioChoice.Unchanged
        applied = target.id
        return RequiredAudioChoice.Select(target.id)
    }
}
