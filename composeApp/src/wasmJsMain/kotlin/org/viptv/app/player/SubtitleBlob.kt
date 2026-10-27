@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

@JsFun("(text) => URL.createObjectURL(new Blob([text], {type:'text/vtt;charset=utf-8'}))")
internal external fun subtitleBlob(text: String): String

@JsFun("(url) => URL.revokeObjectURL(url)")
internal external fun revokeSubtitleBlob(url: String)

@JsFun("(video, callback) => { if(video.requestVideoFrameCallback) video.requestVideoFrameCallback(() => callback()); }")
internal external fun observeBrowserFrame(video: org.w3c.dom.HTMLVideoElement, callback: () -> Unit)

// Keep automatically positioned provider cues above the canvas toolbar/gradient
// on snapped lines ([providerCueLine]). Explicit WebVTT line settings remain the
// provider's choice; the lifted cues are marked so a size change can move them.
@JsFun("(element, offset, line) => { for (const cue of Array.from(element.track.cues || [])) { if (cue.endTime <= offset) { element.track.removeCue(cue); continue; } cue.startTime = Math.max(0, cue.startTime - offset); cue.endTime -= offset; if (cue.line === 'auto') { cue.viptvLifted = true; cue.line = line; } } }")
internal external fun placeProviderCues(element: org.w3c.dom.HTMLTrackElement, offset: Double, line: Int)

@JsFun("(element, line) => { for (const cue of Array.from(element.track.cues || [])) if (cue.viptvLifted) cue.line = line; }")
internal external fun liftProviderCues(element: org.w3c.dom.HTMLTrackElement, line: Int)
