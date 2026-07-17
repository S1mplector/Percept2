# Bundled starter assets

These files are the source assets copied into projects created by the JVN editor.

- `Lavender_test_sprite/` contains the layered sketch of Lavender used by the VNS character presets.
- `demo_bg/game.png` and `demo_bg/menu.png` are original monochrome sketch backgrounds generated for
  JVN with OpenAI's built-in image-generation tool on 2026-07-16, then cropped to 1920x1080.
- `demo_bgm/` contains the starter music and its upstream license document. Keep that document with
  redistributed copies of the track.

The editor build packages this directory as new-project resources. Do not add an asset here without
also ensuring the generated project has a clear use for it and redistribution rights are documented.
