# Desktop GPU render path

The desktop player has two ways to put libmpv frames into the Compose scene. The QA diagnostic line
reports which one a session uses and why:
`path=mpv-software|mpv-gl gl=pending|off|unavailable|failed|on`.

- **Software** (`mpv-software`, the default): mpv's software render context writes each frame into an
  `rgb0` buffer that Skia uploads. Every platform has it.
- **GL** (`mpv-gl`, Linux, opt-in with `-Dviptv.mpvGl=true` or `VIPTV_MPV_GL=1`): an `mpv-render`
  thread with its own GLX context sharing objects with Skiko's renders mpv through its OpenGL render
  context into three RGBA8 textures. On the recording thread the newest texture is wrapped as a Skia
  image on Skiko's own `DirectContext` and drawn with the Compose scene, so no frame passes through
  system memory. That thread reports mpv's swaps, so mpv's pacing no longer waits for a Compose frame.

It stays opt-in until a hardware GPU and an HDR display qualify it (see below).

## Choosing a path

The path is chosen once per session, on the first frame after Skiko's GL context exists. Each of
these falls back to software:

| `gl=` | When |
|---|---|
| `off` | GL not requested |
| `unavailable` | Skiko isn't on `OPENGL` (Xvfb's llvmpipe is on Skiko's blocklist), it isn't Linux, or Skiko's private renderer fields don't match |
| `failed` | the shared GLX context, the textures or mpv's OpenGL render context couldn't be created, or the render thread failed later in the session |

`MpvSkikoGl.kt` reads Skiko's `DirectContext` from a private field of `LinuxOpenGLRenderer`, and takes
the `GLXContext` and `Display` that Skiko has current on the recording thread, so the render thread's
context shares objects with Skiko's own. A Compose/Skiko upgrade can silently turn the GL path into
`unavailable`; check the diagnostic after upgrading.

The render thread only makes its context current and draws. Everything that sends X requests — the
pbuffer, the GLX context — is created and destroyed on the recording thread, so nothing issues X
requests concurrently with Skiko's own frames.

## Frame and texture ownership

`MpvFrameRing` is a triple buffer: the render thread only writes the back slot, the recording thread
only reads the front slot, and the middle slot holds the newest finished frame until the next frame
swaps it in.

`MpvTextureSet.claim()` decides who frees the three textures of a size. The render thread deletes a
set it replaces unless the recording thread adopted it first; an adopted set belongs to the Skia
images that wrapped it, so closing those images deletes the textures with Skiko's GL context current.
The render thread never touches a set the front slot or an image owns, so a resize cannot pull
textures out from under a drawn frame.

## Colour

`mpvColourOptions` sets `target-prim`/`target-trc`/`tone-mapping` before the render context exists, so
mpv's shader tone maps HDR sources to BT.709/sRGB and the `vf` chain from `DESKTOP_COLOUR_OUTPUT.md`
is skipped, leaving hardware decoding on. `target-colorspace-hint=yes` is reserved for a GL path on an
HDR display; no platform reports one yet.

## Qualifying it

`tools/qa_desktop_frames.sh <launcher> [seconds]` encodes a 1080p60 H.264 fixture and plays it for 30 s
on each path through the player QA window (`VIPTV_DESKTOP_PLAYER_QA=1`), then prints the last
diagnostic and the share of played source frames that were drawn; the target is at least 99 %. The
launcher is `composeApp/build/compose/binaries/main/app/VIPTV-Experiment/bin/VIPTV-Experiment` after
`:composeApp:createDistributable`. It needs an X display whose driver Skiko accepts; under Xvfb the GL
run reports `path=mpv-software gl=unavailable`, which measures the fallback, not the GL path.

`MpvGlNativeTest` drives the presenter without Skiko and checks that a second context sharing its
objects reads a published frame top row first. It runs only with `VIPTV_MPV_GL_TEST=1`, because the
software GLX driver this repo's hosts expose creates mpv's OpenGL render context and publishes frames
but draws no picture into them.

So the picture path, the 60 fps target and HDR passthrough all still need a machine with a hardware
GPU, and an HDR display for the last one.