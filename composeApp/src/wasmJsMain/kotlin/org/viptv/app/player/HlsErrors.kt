@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

// Keep the exact JavaScript listener for off(): a Kotlin callback converted twice need not keep its JS identity.
@JsFun("""(hls, event, failed) => {
 const listener = (_, data) => { if (data && data.fatal === true) failed(); };
 hls.on(event, listener);
 return listener;
}""")
internal external fun listenForFatalHlsError(hls: Hls, event: String, failed: () -> Unit): JsAny

@JsFun("""(hls, event, listener) => { hls.off(event, listener); }""")
internal external fun removeHlsErrorListener(hls: Hls, event: String, listener: JsAny)
