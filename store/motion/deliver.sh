#!/bin/sh
# Turns the masters from render.mjs into the published deliverables:
#   web/public/video/   light encodes + posters for the who-called.com home page
#   out/googleplay/     1920×1080 file to upload to YouTube (Play only accepts a YouTube URL)
#   out/appstore/       already produced by `render.mjs --format ios` (886×1920)
set -e
cd "$(dirname "$0")"
WEB=../../web/public/video
mkdir -p "$WEB" out/googleplay
enc() { # in out WxH
  ffmpeg -y -loglevel error -i "$1" -vf "scale=$3:flags=lanczos" -c:v libx264 -preset slow -crf 27 -profile:v high \
    -pix_fmt yuv420p -c:a aac -b:a 96k -movflags +faststart "$2"
}
for l in fr en; do
  cp "out/master/who-called-$l-16x9.mp4" "out/googleplay/who-called-$l-youtube-1920x1080.mp4"
  enc "out/master/who-called-$l-16x9.mp4" "$WEB/who-called-$l-16x9.mp4" 1280:720
  enc "out/master/who-called-$l-9x16.mp4" "$WEB/who-called-$l-9x16.mp4" 720:1280
  # poster = end card (logo + URL), shown before playback and with reduced motion
  ffmpeg -y -loglevel error -ss 22.8 -i "out/master/who-called-$l-16x9.mp4" -frames:v 1 -vf scale=1280:720 -q:v 4 "$WEB/who-called-$l-16x9.jpg"
  ffmpeg -y -loglevel error -ss 22.8 -i "out/master/who-called-$l-9x16.mp4" -frames:v 1 -vf scale=720:1280 -q:v 4 "$WEB/who-called-$l-9x16.jpg"
done
ls -lh "$WEB" out/googleplay out/appstore
