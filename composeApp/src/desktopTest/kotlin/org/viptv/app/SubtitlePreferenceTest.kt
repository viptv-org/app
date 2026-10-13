package org.viptv.app

import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.preferredProviderSubtitle
import org.viptv.engine.wire.SubtitleCandidate
import kotlin.test.*

class SubtitlePreferenceTest {
    @Test fun languageAliasesAndRegionsChooseTheRequestedProviderTrack() {
        fun input(language: String?, enabled: Boolean = true) = PlaybackInput("fixture", "https://media.example/fixture",
            preferences = ProfilePreferences(preferred_subtitle_language = language, subtitles_enabled = enabled))
        val english = SubtitleCandidate("english", "addon", "eng", "English")
        val german = SubtitleCandidate("german", "addon", "ger", "German")
        val portugal = SubtitleCandidate("portugal", "addon", "pt-PT", "Portuguese")
        val brazil = SubtitleCandidate("brazil", "addon", "por-BR", "Portuguese Brazil")
        val candidates = listOf(english, german, portugal, brazil)
        assertEquals(english, preferredProviderSubtitle(input("en"), candidates))
        assertEquals(german, preferredProviderSubtitle(input("de"), candidates))
        assertEquals(brazil, preferredProviderSubtitle(input("pt-BR"), candidates))
        assertEquals(portugal, preferredProviderSubtitle(input("pt"), candidates))
        assertNull(preferredProviderSubtitle(input("en", false), candidates))
        assertNull(preferredProviderSubtitle(input("ja"), candidates))
        assertNull(preferredProviderSubtitle(input(null), candidates))
    }
}
