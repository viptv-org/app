@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

@JsFun("(text) => URL.createObjectURL(new Blob([text], {type:'text/vtt;charset=utf-8'}))")
internal external fun subtitleBlob(text: String): String

@JsFun("(url) => URL.revokeObjectURL(url)")
internal external fun revokeSubtitleBlob(url: String)

@JsFun("(video, callback) => { if(video.requestVideoFrameCallback) video.requestVideoFrameCallback(() => callback()); }")
internal external fun observeBrowserFrame(video: org.w3c.dom.HTMLVideoElement, callback: () -> Unit)

// Keep automatically positioned provider cues above the canvas toolbar/gradient.
// Explicit WebVTT line settings remain the provider's choice. The cue stays on
// snapped lines: its last line sits where a one-line cue used to (about 72 %),
// multi-line cues grow upward, long lines wrap, and the browser stacks
// simultaneous cues. A percentage line turns that off and cues overlap (app#43).
@JsFun("(element, offset) => { for (const cue of Array.from(element.track.cues || [])) { if (cue.endTime <= offset) { element.track.removeCue(cue); continue; } cue.startTime = Math.max(0, cue.startTime - offset); cue.endTime -= offset; if (cue.line === 'auto') cue.line = -5; } }")
internal external fun placeProviderCues(element: org.w3c.dom.HTMLTrackElement, offset: Double)
