package org.viptv.app.state

data class SettingsPage(val section: String? = null, val subpage: Boolean = false) {
    fun laidOut(compact: Boolean, root: String? = null) = copy(subpage = compact && section != null && section != root)

    fun parent(): SettingsPage? = if (subpage) SettingsPage() else null
}
