@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

import org.w3c.dom.HTMLVideoElement

@JsFun("""(video, blocked, failed) => {
 const failure = error => { if (error.name === 'NotAllowedError') blocked(); else if (error.name !== 'AbortError') failed(); };
 try { const result = video.play(); if (result && result.catch) result.catch(failure); } catch (error) { failure(error); }
}""")
internal external fun requestBrowserPlay(video: HTMLVideoElement, blocked: () -> Unit, failed: () -> Unit)
