package org.viptv.app.player

import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill

internal fun fillVideoGravity(fill: Boolean) = if (fill) AVLayerVideoGravityResizeAspectFill else AVLayerVideoGravityResizeAspect
