#!/usr/bin/env bash
# Synthetic, silent assets only. Output is caller-owned and outside served bundles.
set -euo pipefail
runtime_media=${1:?Pass an empty owned media directory}
runtime_seconds=${2:-420}
[[ "$runtime_seconds" =~ ^[0-9]+$ ]] && (( runtime_seconds >= 420 && runtime_seconds <= 3600 ))
mkdir -p "$runtime_media"
ffmpeg -hide_banner -loglevel error -f lavfi -i testsrc2=size=320x180:rate=15 \
  -f lavfi -i anullsrc=r=48000:cl=stereo -t "$runtime_seconds" -c:v libx264 -preset ultrafast \
  -threads 1 -b:v 160k -g 30 -sc_threshold 0 -c:a aac -b:a 64k \
  -f hls -hls_time 2 -hls_list_size 0 \
  -hls_segment_filename "$runtime_media/segment-%03d.ts" "$runtime_media/all.m3u8"
ffmpeg -hide_banner -loglevel error -f lavfi -i testsrc2=size=320x180:rate=24 \
  -f lavfi -i anullsrc=r=48000:cl=stereo -t 35 -c:v libx264 -preset ultrafast \
  -threads 1 -b:v 180k -c:a eac3 -b:a 192k "$runtime_media/format-eac3.mkv"
