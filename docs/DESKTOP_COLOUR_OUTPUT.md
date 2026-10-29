# Desktop colour output

The desktop player presents libmpv through the render API's software context: each frame is
an 8-bit `rgb0` raster handed to Skia and composited as sRGB. mpv's colour management needs
`--vo=gpu-next` (`--target-colorspace-hint` requires it, and `--target-prim`, `--target-trc`,
`--target-peak` and `--tone-mapping` have no effect on this path), and `vo=gpu-next` cannot be
selected under the render API, so the software context does no colour conversion of its own.

`mpvToneMapFilter` (`MpvColourOutput.kt`) tone maps PQ/HLG sources into that raster: zscale to
linear at a 100-nit nominal peak, BT.709 gamut, `tonemap=mobius`, BT.709/sRGB, 8-bit 4:2:0. On
this host (mpv 0.37, libplacebo 6.338.2, software display) a 0–10 000 nit PQ ramp renders as
0→216 and plateaus above about 1 000 nits. Möbius leaves in-range values untouched, so SDR
sources keep their own colours and must not get the chain: it darkens them.

The decision is made once per session in `MpvSession.open()`, from `video-params/gamma`, before
presentation is authorized, so no clipped frame is shown. That property is unset at the
file-loaded event and arrives with the first decoded frame; the wait is bounded at 3 s, and
files without a video track skip it. If the runtime lacks zimg's zscale or libavfilter's tonemap,
the `vf` command fails and playback continues with the clipped image.

The chain runs on CPU frames, so hardware decoding falls back for HDR sources and 4K HDR tone
mapping is CPU-bound.

## HDR output

The player outputs SDR. Passing PQ/BT.2020 to the compositor needs mpv to draw into a GL/Vulkan
target the Compose scene owns, `--target-colorspace-hint=yes`, and a per-platform HDR query;
without those the tone map above is the whole HDR story.

## Checking it

The scripted desktop player QA reports `tone_map=on|off` beside `path=mpv-software`. `MpvNativeTest`
asserts the packaged runtime accepts the chain, so a runtime without zimg fails the desktop tests
rather than playback.
