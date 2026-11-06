package org.viptv.app.state

// The opening screen's stages, in the order a start passes through them.
enum class StartupStage { Discovery, Opening, Buffering }

// fill is the share of the title art drawn at full opacity, measured up from its bottom edge.
data class StartupProgress(val stage: StartupStage, val fill: Float, val holding: Boolean, val status: String)

// Every value here is a signal the app already has; nothing is estimated.
internal data class StartupSignals(
    val discovering: Boolean = false,
    val sourcesArrived: Boolean = false,
    val opening: Boolean = false,
    val openingStage: String? = null,
    val buffering: Boolean = false,
    val bufferedFraction: Float? = null,
) {
    val inFlight: Boolean get() = discovering || opening || buffering
}

// Discovery and ranking report no share of their own, so the fill holds until the source opens.
private const val OPENING_START = 1f / 3f
private const val BUFFERING_START = 2f / 3f

internal fun ApplicationState.startupSignals(bufferedFraction: Float?): StartupSignals = StartupSignals(
    discovering = findingSources,
    sourcesArrived = sources.isNotEmpty(),
    opening = openingSource != null && !(playback != null && presentation == null),
    openingStage = openingStage,
    buffering = playback != null && presentation == null,
    bufferedFraction = bufferedFraction,
)

internal fun startupProgress(previous: StartupProgress?, signals: StartupSignals): StartupProgress? {
    if (!signals.inFlight) return null
    val current = when {
        signals.buffering -> StartupStage.Buffering
        signals.opening -> StartupStage.Opening
        else -> StartupStage.Discovery
    }
    val stage = if (previous != null && previous.stage.ordinal > current.ordinal) previous.stage else current
    val measured = when (stage) {
        StartupStage.Discovery -> 0f
        StartupStage.Opening -> OPENING_START
        StartupStage.Buffering -> signals.bufferedFraction?.let { BUFFERING_START + (1f - BUFFERING_START) * it.coerceIn(0f, 1f) }
            ?: BUFFERING_START
    }
    return StartupProgress(
        stage = stage,
        fill = maxOf(previous?.fill ?: 0f, measured),
        holding = when (stage) {
            StartupStage.Discovery, StartupStage.Opening -> true
            StartupStage.Buffering -> signals.bufferedFraction == null
        },
        status = when (stage) {
            StartupStage.Discovery -> if (signals.sourcesArrived) "Still checking sources" else "Finding sources"
            StartupStage.Opening -> signals.openingStage ?: "Opening stream…"
            StartupStage.Buffering -> "Buffering…"
        },
    )
}