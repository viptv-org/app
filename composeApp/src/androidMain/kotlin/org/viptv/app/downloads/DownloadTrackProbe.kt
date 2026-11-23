package org.viptv.app.downloads

import android.media.MediaExtractor
import android.media.MediaFormat

internal object MediaExtractorProbe : DownloadProbe {
    override fun tracks(path: String): List<DownloadTrack> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            return (0 until extractor.trackCount).mapNotNull { index ->
                val format = extractor.getTrackFormat(index)
                val kind = downloadTrackKind(format.getString(MediaFormat.KEY_MIME)) ?: return@mapNotNull null
                val language = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) format.getString(MediaFormat.KEY_LANGUAGE) else null
                DownloadTrack(kind, downloadTrackLanguage(language), null, embedded = true)
            }
        } finally {
            extractor.release()
        }
    }
}
