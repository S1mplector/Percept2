# JVN Engine Hub Announcements

Format:

    ## YYYY-MM-DD — Short title
    Body text describing what changed. Multiple paragraphs are fine; blank
    lines separate them. The block ends at the next "## " header or end of file.

----

## 2026-04-27 — Expanded VNS particle ambience presets
The VNS particle preset library now has tuned implementations for `sakura`, `fireflies`, `dust`, and `leaves` instead of routing those preset names through the neutral fallback.

Use `[particles preset=sakura]`, `[pfx fireflies intensity=0.4]`, `[weather dust opacity=0.5]`, or `[fx leaves wind=-20 tint=#ccdd7722]` to add scene-wide petals, night glows, floating motes, and autumn leaves. The existing shaping options still apply: `intensity`, `layer`, `opacity`, `speed`, `wind`, `duration`, and `tint`.

Focused preset tests cover the new tuning so these ambience effects keep their scene-sized spawn areas, alpha behavior, wind handling, and blend modes stable.

## 2026-04-27 — Documentation update covering the engine hub
New documentation has been added regarding the engine hub. You can read engine-hub.md for more information. 

## 2026-04-27 — Particle FX presets for VNS
VNS particle effects now support richer weather controls and render in game through the JavaFX VN renderer. Use `[particles]`, `[weather]`, `[pfx]`, or `[fx]` with presets like `snow` and `rain`, plus shaping options for `intensity`, `layer`, `opacity`, `speed`, `wind`, `duration`, and `tint`.

Snow and rain now use tuned scene-wide spawn areas, wind drift, opacity/tint scaling, and layer-aware rendering alongside characters and the audio visualizer. Timed effects automatically expire, while stop commands let existing particles fade out instead of disappearing abruptly.

Editor autocomplete and hover docs were updated for the expanded command surface, and focused parser/runtime tests cover the new options, presets, stop behavior, and duration expiry.

## 2026-04-27 — Puppeteer GIF export: Reveal in Folder
The "Recording Complete" dialog now has a "Reveal in Folder" action next to Close. One click opens the export directory in your OS file manager. Falls back gracefully on headless or sandboxed environments where desktop integration isn't available.
