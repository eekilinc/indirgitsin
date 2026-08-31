#!/usr/bin/env bash
# Synthetic test media only. FFmpeg is not linked into or shipped with the application.
set -euo pipefail
target=app/src/androidTest/assets/live
mkdir -p "$target"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'testsrc=size=64x64:rate=24' \
  -f lavfi -i 'sine=frequency=440:sample_rate=44100' -t 8 -c:v libx264 -preset ultrafast \
  -pix_fmt yuv420p -g 48 -sc_threshold 0 -c:a aac -b:a 96k -f hls -hls_time 2 -hls_list_size 0 \
  -hls_segment_filename "$target/ts-%02d.ts" "$target/ts.m3u8"
cat "$target"/ts-*.ts > "$target/capture.ts"
ffmpeg -hide_banner -loglevel error -y -i "$target/capture.ts" -c copy \
  -movflags empty_moov+frag_keyframe+default_base_moof "$target/capture-fmp4.mp4"
