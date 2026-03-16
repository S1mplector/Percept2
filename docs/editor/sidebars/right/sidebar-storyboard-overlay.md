# Storyboard Overlay

The `Storyboard Overlay` sidebar utility lets the team load storyboard stills and ghost them over the active JES or VNS preview. It is meant for staging reference while placing characters, tuning framing, or checking shot continuity against board art.

## What It Does

- scans a storyboard folder automatically, with manual folder override when boards live elsewhere
- lists available board frames in a sidebar-friendly picker with quick filter
- draws the selected frame over the preview with adjustable opacity
- supports next/previous frame switching while you work
- fits the overlay to the project viewport from `jvn.project`, so 1:1 boards line up with the engine preview

## Where It Appears

- open it from `View > Panels > Storyboard Overlay`
- or from `Tools > Storyboard Overlay`
- or from the sidebar `+` chooser on either side

## Notes

- the overlay is editor-only and does not ship in runtime builds
- if no storyboard-style folder is found, the panel falls back to project images so you can still point it at the right reference set
- state is stored in `.jvn/storyboard-overlay.properties`
