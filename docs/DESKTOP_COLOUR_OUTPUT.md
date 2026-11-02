# Desktop colour output

By default the desktop player presents libmpv through the render API's software context: each
frame is an 8-bit `rgb0` raster handed to Skia and composited as sRGB. That context has no shader
stage, so mpv does no colour conversion of its own and `mpvToneMapFilter` tone maps PQ/HLG into it.
The opt-in GL path (`DESKTOP_GPU_RENDER.md`) runs mpv's own tone map instead and skips the filter.

`mpvToneMapFilter` (`MpvColourOutput.kt`) tone maps PQ/HLG sources into that raster: zscale to
linear at a 100-nit nominal peak, BT.709 gamut, `tonemap=mobius`, BT.709/sRGB, 8-bit 4:2:0. On
this host (mpv 0.37, libplacebo 6.338.2, software display) a 0–10 000 nit PQ ramp renders as
0→216 and plateaus above about 1 000 nits. Möbius leaves in-range values untouched, so SDR
sources keep their own colours and must not get the chain: it darkens them.

The decision is made once per session in `MpvSession.open()`, from `video-params/gamma`, before
presentation is authorized, so no clipped frame is shown. That property is unset at the
file-loaded event and arrives with the first decoded frame; the wait is bounded at 3 s, and
files without a video track skip it. If the runtime lacks zimg's zscale or libavfilter's tonemap,
the `vf` command fails and playback continues with the clipped image. Homebrew's `ffmpeg` has no
zimg, so the macOS bundle (`tools/build_mpv.py`) takes its FFmpeg libraries from `ffmpeg-full`.

The chain runs on CPU frames, so hardware decoding falls back for HDR sources and 4K HDR tone
mapping is CPU-bound.

## HDR output

Neither path outputs HDR. `mpvColourOptions` reserves `--target-colorspace-hint=yes` for the GL path
on an HDR display, but no platform reports one yet, Skiko's GL surface is an RGBA8 sRGB target, and
the render API's OpenGL context runs `vo_gpu`, which has no colourspace hint to send.

## Checking it

The scripted desktop player QA reports `tone_map=on|off` beside `path=`. `MpvNativeTest`
asserts the packaged runtime accepts the chain, so a runtime without zimg fails the desktop tests
rather than playback.
