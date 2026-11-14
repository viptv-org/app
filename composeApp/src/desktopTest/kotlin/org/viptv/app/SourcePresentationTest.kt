package org.viptv.app

import kotlin.test.*
import org.viptv.app.features.sources.*
import org.viptv.engine.wire.*

class SourcePresentationTest {
    @Test fun theRowShowsTheAddOnsOwnLinesAndNotARebuiltTechnicalLine() {
        val description = "Series.S01E04.2160p.WEB-DL\nRelease group\nEnglish / Japanese"
        val candidate = SourceCandidate("grant", "addon:1", "Provider 4K", detail = description,
            presentation = SourcePresentation(video_codec = "HEVC", audio_codecs = listOf("EAC3")))
        assertEquals(description, candidate.addonText())
        assertNull(candidate.copy(detail = null).addonText())
        assertNull(candidate.sizeLabel(), "a codec and an audio codec never become a size")
    }

    @Test fun structuredFieldsDoNotBecomeDisplayFacts() {
        val candidate = SourceCandidate("grant", "vsources", "Release", presentation = SourcePresentation(
            provider_name = "Provider", source_id = "upstream", delivery_format = "progressive",
            video_codec = "HEVC", audio_codecs = listOf("EAC3"), size_label = "2.4 GB"))
        assertEquals("Provider", candidate.providerLabel())
        assertNull(candidate.addonText())
        assertNull(candidate.sizeLabel(), "a size read from the release name is not the add-on's own size")
        assertEquals(emptyList(), candidate.sourceTags())
    }

    @Test fun filtersKeepSameNamedProvidersSeparate() {
        val a = SourceCandidate("a", "addon-a", "1080p", presentation = SourcePresentation(provider_name = "Same"))
        val b = SourceCandidate("b", "addon-b", "1080p", presentation = SourcePresentation(provider_name = "Same"))
        assertNotEquals(a.sourceFilterId(), b.sourceFilterId())
        assertNull(a.qualityLabel())
    }
}
