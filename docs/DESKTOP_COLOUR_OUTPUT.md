# Desktop colour output

The desktop player presents libmpv through the render API's software context: each frame is
an 8-bit `rgb0` raster handed to Skia and composited as sRGB. mpv's colour management needs
`--vo=gpu-next` (`--target-colorspace-hint` requires it, and `--target-prim`, `--target-trc`,
`--target-peak` and `--tone-mapping` have no effect on this path), and `vo=gpu-next` cannot be
selected under the render API. The software context therefore does no colour conversion of its
own: a PQ/HLG source reaches Skia with its code values read as sRGB, so everything above
roughly 93 nits clips to white.

`MpvColourOutput.mpvToneMapFilter` is applied to those sources: zscale to linear at a 100-nit
nominal peak, BT.709 gamut, `tonemap=mobius`, BT.709/sRGB, 8-bit 4:2:0. Measured on this host
(mpv 0.37, libplacebo 6.338.2, software display) with a 0–10 000 nit PQ ramp: without the chain
16 of 32 sampled columns are 255 and the gradient is gone; with it the ramp runs 0→216 and only
plateaus above about 1 000 nits. Möbius leaves in-range values untouched, so an SDR source keeps
its colours and must not get the chain (it would darken them).

The decision is made once per session in `MpvSession.open()`, from `video-params/gamma`, before
presentation is authorized, so no clipped frame is shown. That property is unset at the
file-loaded event and arrives with the first decoded frame; the wait is bounded at 3 s, and
files without a video track skip it. If the runtime lacks zimg's zscale or libavfilter's tonemap,
the `vf` command fails and playback continues with the clipped image.

The chain runs on CPU frames, so hardware decoding falls back for HDR sources and 4K HDR tone
mapping is CPU-bound until a GPU path exists.

## HDR output

Not implemented. Passing PQ/BT.2020 to the compositor needs mpv to draw into a GL/Vulkan target
the Compose scene owns, `--target-colorspace-hint=yes`, and a per-platform HDR query. app#237
owns that work.

## Checking it

The scripted desktop player QA reports `tone_map=on|off` beside `path=mpv-software`. `MpvNativeTest`
asserts the packaged runtime accepts the chain, so a runtime without zimg fails the desktop tests
rather than playback.
