@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.viptv.experiment

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/** Read the actual app composition. RootForTest is Compose's test inspection marker. */
internal object PerformanceQaSemantics {
    private fun roots(view: View): List<SemanticsNode> = buildList {
        (view as? RootForTest)?.let { add(it.semanticsOwner.rootSemanticsNode) }
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(roots(view.getChildAt(index)))
    }
    fun nodes(view: View): List<SemanticsNode> = buildList {
        fun visit(node: SemanticsNode) {
            if (!node.layoutInfo.isAttached || !node.layoutInfo.isPlaced) return
            if (!node.isTransparent && node.boundsInWindow.width > 0 && node.boundsInWindow.height > 0) add(node)
            node.children.forEach(::visit)
        }
        roots(view).forEach(::visit)
    }
    fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    fun texts(view: View) = nodes(view).flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    }
    fun interactive(view: View) = nodes(view).any {
        it.config.getOrNull(SemanticsActions.OnClick)?.action != null && !it.config.contains(SemanticsProperties.Disabled)
    }
    fun focus(view: View, label: String): Boolean = nodes(view).firstOrNull {
        text(it) == label && it.config.getOrNull(SemanticsActions.RequestFocus)?.action != null
    }?.config?.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke() == true
    fun focusedText(view: View) = nodes(view).filter { it.config.getOrNull(SemanticsProperties.Focused) == true }.joinToString(" ", transform = ::text)
}
