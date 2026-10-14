package org.viptv.app

import kotlin.test.*
import org.viptv.app.features.sources.*
import org.viptv.engine.wire.*

class SourcePresentationTest {
    @Test fun structuredFieldsDoNotGuessContainerFromDeliveryFormat() {
        val candidate = SourceCandidate("grant", "vsources", "Release", presentation = SourcePresentation(
            provider_name = "Provider", source_id = "upstream", delivery_format = "progressive",
            video_codec = "HEVC", audio_codecs = listOf("EAC3"), size_label = "2.4 GB"))
        assertEquals("Provider", candidate.providerLabel())
        assertNull(candidate.formatLabel())
        assertEquals("HEVC · EAC3 · 2.4 GB", candidate.technicalDetail())
    }
    @Test fun filtersKeepSameNamedProvidersSeparate() {
        val a = SourceCandidate("a", "addon-a", "1080p", presentation = SourcePresentation(provider_name = "Same"))
        val b = SourceCandidate("b", "addon-b", "1080p", presentation = SourcePresentation(provider_name = "Same"))
        assertNotEquals(a.sourceFilterId(), b.sourceFilterId())
        assertNull(a.qualityLabel()) // release names are not trusted quality metadata
        assertNull(a.formatLabel())
    }
}
