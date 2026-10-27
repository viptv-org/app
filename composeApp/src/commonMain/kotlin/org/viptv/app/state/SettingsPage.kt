package org.viptv.app.state

/**
 * The page Settings shows. A null [section] is the Settings list on a phone-width layout and the
 * default section on wider ones. A [subpage] was opened from the Settings list (every section on a
 * phone-width layout), so Back returns to the list instead of leaving Settings. Wider layouts pick
 * sections from a sidebar; Back leaves Settings from any of them.
 */
data class SettingsPage(val section: String? = null, val subpage: Boolean = false) {
    /**
     * This page as laid out: at phone width every section is a sub-page of the Settings list,
     * except a [root] section, where the visit began and which Back leaves Settings from (app#44).
     */
    fun laidOut(compact: Boolean, root: String? = null) = copy(subpage = compact && section != null && section != root)

    /** The page Back returns to, or null when Back leaves Settings. */
    fun parent(): SettingsPage? = if (subpage) SettingsPage() else null
}
