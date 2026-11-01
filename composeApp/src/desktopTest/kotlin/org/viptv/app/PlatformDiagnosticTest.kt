package org.viptv.app

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import kotlinx.coroutines.runBlocking
import org.viptv.app.features.settings.systemReducedMotion
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.mpv.MpvSession
import org.viptv.app.resources.Res
import java.io.File
import kotlin.test.Test

class PlatformDiagnosticTest {
    @Test fun report() = runBlocking {
        println("DIAG os=${System.getProperty("os.name")} headless=${java.awt.GraphicsEnvironment.isHeadless()}")
        println("DIAG reducedMotion=${systemReducedMotion(PlatformContext.INSTANCE)}")
        runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.forEach { println("DIAG display=${it.displayMode.width}x${it.displayMode.height}") }
        }.onFailure { println("DIAG display failed $it") }
        val file = File.createTempFile("diag", ".png")
        val image = java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB)
        javax.imageio.ImageIO.write(image, "png", file)
        val context = PlatformContext.INSTANCE
        val loader = ImageLoader.Builder(context).build()
        val candidates = listOf("res" to Res.getUri("files/ui-preview/landscape.jpg"), "toURI" to file.toURI().toString(),
            "toPathUri" to file.toPath().toUri().toString(), "absolutePath" to file.absolutePath,
            "fileObject" to file, "okioPath" to okio.Path.Companion.run { file.absolutePath.toPath() })
        for ((name, data) in candidates) {
            val result = loader.execute(ImageRequest.Builder(context).data(data).build())
            println("DIAG coil $name data=$data -> ${result::class.simpleName} ${(result as? ErrorResult)?.throwable}")
        }
        val filters = listOf("zscale=t=linear:npl=100", "format=gbrpf32le", "zscale=p=bt709", "tonemap=tonemap=mobius:desat=0",
            "zscale=t=bt709:m=bt709:r=tv", "format=yuv420p", "lavfi=[zscale=t=linear:npl=100]", "scale", "format=yuv420p,tonemap=tonemap=mobius")
        val session = MpvSession(PlaybackInput("owned", "about:blank"))
        try {
            for (filter in filters) {
                val outcome = runCatching { session.command("vf", "set", filter) }
                println("DIAG mpv vf=$filter -> ${outcome.exceptionOrNull() ?: "ok"}")
            }
        } finally { session.closeAfterRenderer() }
    }
}
