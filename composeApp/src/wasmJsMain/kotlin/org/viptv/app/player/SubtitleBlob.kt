@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

@JsFun("(text) => URL.createObjectURL(new Blob([text], {type:'text/vtt;charset=utf-8'}))")
internal external fun subtitleBlob(text: String): String

@JsFun("(url) => URL.revokeObjectURL(url)")
internal external fun revokeSubtitleBlob(url: String)

@JsFun("(video, callback) => { if(video.requestVideoFrameCallback) video.requestVideoFrameCallback(() => callback()); }")
internal external fun observeBrowserFrame(video: org.w3c.dom.HTMLVideoElement, callback: () -> Unit)

// Keep automatically positioned provider cues above the canvas toolbar/gradient.
// Explicit WebVTT line settings remain the provider's choice.
@JsFun("(element, offset) => { for (const cue of Array.from(element.track.cues || [])) { if (cue.endTime <= offset) { element.track.removeCue(cue); continue; } cue.startTime = Math.max(0, cue.startTime - offset); cue.endTime -= offset; if (cue.line === 'auto') { cue.snapToLines = false; cue.line = 72; } } }")
internal external fun placeProviderCues(element: org.w3c.dom.HTMLTrackElement, offset: Double)
