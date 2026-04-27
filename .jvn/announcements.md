# JVN Engine Hub — Announcements

This file is read by the Engine Hub on startup and after every "Update Engine" pull. Add a new entry above the others using the format below; the hub shows them latest-first, with the date and title bold and the body underneath.

Format:

    ## YYYY-MM-DD — Short title
    Body text describing what changed. Multiple paragraphs are fine; blank
    lines separate them. The block ends at the next "## " header or end of file.

----

## 2026-04-27 — Puppeteer GIF export: Reveal in Folder
The "Recording Complete" dialog now has a "Reveal in Folder" action next to Close. One click opens the export directory in your OS file manager. Falls back gracefully on headless or sandboxed environments where desktop integration isn't available.