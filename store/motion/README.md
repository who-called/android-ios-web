# Who Called — promo film (HTML motion template)

23 s motion piece, 8 scenes: ringing call → spam flood → shield impact → verify (spam score) → report (community network) → SMS shield → values → outro. Sound design is synthesized in code (`sfx.mjs`), so there are no third-party samples or licences.

- **Preview**: open `index.html` (space = play/pause, scrub bar, 🔇/🔊 sound, language and format selectors).
  URL params: `?lang=fr|en|es`, `?format=16x9|9x16|ios`, `?t=12.5` (freeze on a frame).
- **Texts**: everything on screen lives in `i18n.js`. Add a language by copying the `fr` block.
- **App screens**: the phone shows the real store-listing screens (Verify, Report, SMS Shield) from `store/screens.mjs`, the same module `store/generate.mjs` uses for the Play/App Store screenshots. Their texts are the `app` block of `i18n.js`. `node build-screens.mjs` regenerates `screens.gen.js` (done automatically by `render.mjs`), so a change to a store screen shows up in the film too.
  Oversized display words shrink automatically to fit the frame.
- **Sound**: `node sfx.mjs` regenerates `sfx.wav` (export, git-ignored, created on demand by `render.mjs`) and `sfx.m4a` (preview), mastered at -16 LUFS. Cue times mirror the GSAP timeline, so if you retime a scene in `index.html`, move its cues too.

## Export (needs Google Chrome + ffmpeg)

```bash
npm install
node render.mjs --lang fr --format 16x9  --out out/master/who-called-fr-16x9.mp4
node render.mjs --lang fr --format 9x16  --out out/master/who-called-fr-9x16.mp4
node render.mjs --lang fr --format ios   --out out/appstore/who-called-fr-appstore-886x1920.mp4
./deliver.sh   # web encodes + posters → web/public/video, YouTube file → out/googleplay
```

Each frame is rendered on its own from a paused GSAP timeline, so the output is deterministic with no dropped frames (about 4 min per file at 30 fps).

## Store specs

| Store | Deliverable | Spec |
|---|---|---|
| Google Play | `out/googleplay/*-youtube-1920x1080.mp4` | Play only takes a **YouTube URL** (public or unlisted, no ads, embedding allowed). Add it under *Main store listing → Video*. |
| App Store | `out/appstore/*-appstore-886x1920.mp4` | App preview for iPhone 6.9"/6.5": 886×1920, 15–30 s, 30 fps, H.264 High, AAC 256 kb/s stereo. Upload in App Store Connect → version → *Previews and Screenshots*, per locale. |
