# JVN Engine Hub Announcements

Format:

    ## YYYY-MM-DD — Short title
    Body text describing what changed. Multiple paragraphs are fine; blank
    lines separate them. The block ends at the next "## " header or end of file.

----

## 2026-04-27 — Particle FX presets for VNS
VNS particle effects now support richer weather controls and render in game through the JavaFX VN renderer. Use `[particles]`, `[weather]`, `[pfx]`, or `[fx]` with presets like `snow` and `rain`, plus shaping options for `intensity`, `layer`, `opacity`, `speed`, `wind`, `duration`, and `tint`.

Snow and rain now use tuned scene-wide spawn areas, wind drift, opacity/tint scaling, and layer-aware rendering alongside characters and the audio visualizer. Timed effects automatically expire, while stop commands let existing particles fade out instead of disappearing abruptly.

Editor autocomplete and hover docs were updated for the expanded command surface, and focused parser/runtime tests cover the new options, presets, stop behavior, and duration expiry.

## 2026-04-27 — Puppeteer GIF export: Reveal in Folder
The "Recording Complete" dialog now has a "Reveal in Folder" action next to Close. One click opens the export directory in your OS file manager. Falls back gracefully on headless or sandboxed environments where desktop integration isn't available.
