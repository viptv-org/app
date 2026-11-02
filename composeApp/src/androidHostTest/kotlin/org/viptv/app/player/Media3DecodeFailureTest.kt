package org.viptv.app.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class Media3DecodeFailureTest {
    @Test fun decoderFailuresClassifyAsUndecodable() {
        assertTrue(media3DecodeFailure(PlaybackException("decoder init", null, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)))
        assertTrue(media3DecodeFailure(PlaybackException("decoder query", null, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED)))
        assertTrue(media3DecodeFailure(PlaybackException("decoding", null, PlaybackException.ERROR_CODE_DECODING_FAILED)))
        assertTrue(media3DecodeFailure(PlaybackException("capabilities", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES)))
        assertTrue(media3DecodeFailure(PlaybackException("unsupported", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)))
        assertTrue(media3DecodeFailure(PlaybackException("reclaimed", null, PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED)))
    }

    @Test fun transportAndParsingFailuresStayGeneric() {
        assertFalse(media3DecodeFailure(PlaybackException("connection", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
        assertFalse(media3DecodeFailure(PlaybackException("timeout", null, PlaybackException.ERROR_CODE_TIMEOUT)))
        assertFalse(media3DecodeFailure(PlaybackException("container", null, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)))
    }
}