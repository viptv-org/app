package org.viptv.app.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalReducedMotion = staticCompositionLocalOf { false }

internal object Motion {
    const val response = 120
    const val arrive = 200
    const val panel = 300
    const val leave = 200
    const val card = .98f
    const val button = .97f
    const val row = .99f
    const val control = .94f
    val standard = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val exit = CubicBezierEasing(.3f, 0f, 1f, 1f)
}

@Composable
internal fun <T> motionSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spec
