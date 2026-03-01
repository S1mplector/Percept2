# VNS Audio Commands

Complete reference for audio playback, control, and mixing in VNS scripts.

Interop handler: `core/src/main/java/com/jvn/core/vn/DefaultVnInterop.java`

---

## Audio Channels

JVN has three independent audio channels:

| Channel | Purpose | Default Behavior |
|---------|---------|-----------------|
| **BGM** | Background music | Loops continuously |
| **SFX** | Sound effects | One-shot playback |
| **Voice** | Character voices | One-shot playback |

Each channel has its own volume control, and they can be independently paused, resumed, or stopped.

---

## Basic Playback

### Background Music

```vns
# Start playing BGM (loops by default)
[bgm assets/audio/bgm/main_theme.ogg]

# Stop BGM immediately
[bgm_stop]

# Fade out BGM over 2 seconds
[bgm_fadeout 2000]

# Fade out with default duration
[bgm_fadeout]
```

### Sound Effects

```vns
# Play a one-shot sound effect
[sfx assets/audio/sfx/door_open.ogg]
[sfx assets/audio/sfx/sword_clash.ogg]
[sfx assets/audio/sfx/coin_pickup.ogg]

# Stop current SFX
[sfx_stop]
```

### Voice

```vns
# Play a voice clip
[voice assets/audio/voices/hero/line_001.ogg]
[voice assets/audio/voices/narrator/intro.ogg]

# Stop voice playback
[voice_stop]
```

---

## Advanced BGM Control

### Pause and Resume

```vns
# Pause BGM (preserves playback position)
[bgm_pause]

# Resume from where it was paused
[bgm_resume]
```

### Seek

Jump to a specific position in the current BGM track:

```vns
# Seek to 30.5 seconds
[bgm_seek 30.5]

# Seek to the beginning
[bgm_seek 0]
```

### Crossfade

Smoothly transition from the current BGM to a new track:

```vns
# Crossfade to battle theme over 1.5 seconds
[bgm_crossfade assets/audio/bgm/battle_theme.ogg 1500]

# Crossfade with explicit loop setting
[bgm_crossfade assets/audio/bgm/calm_theme.ogg 2000 true]

# Crossfade without looping (play once)
[bgm_crossfade assets/audio/bgm/victory_fanfare.ogg 1000 false]
```

The crossfade simultaneously fades out the old track and fades in the new one.

---

## Global Audio Control

### Stop All

```vns
# Stop all audio channels at once
[audio_stop_all]
```

### Pause / Resume All

```vns
# Pause all audio (useful during menus or cutscenes)
[audio_pause_all]

# Resume all audio
[audio_resume_all]
```

### Raw Audio Provider

For advanced backend control, use the `[audio]` command which forwards directly to the audio interop:

```vns
[audio pause]
[audio resume]
[audio seek 45.0]
[audio crossfade assets/audio/bgm/new_track.ogg 2000]
[audio stop_all]
```

---

## Volume Control

Adjust per-channel volume (range 0.0 to 1.0):

```vns
# Set BGM volume to 50%
[volume bgm 0.5]

# Set SFX volume to full
[volume sfx 1.0]

# Set voice volume to 80%
[volume voice 0.8]

# Mute BGM
[volume bgm 0]
```

Volume changes are reflected immediately and persist in save data through `VnSettings`.

---

## Audio Visualizer

JVN includes an optional in-scene audio visualizer overlay:

```vns
# Enable the visualizer
[visualizer on]

# Enable with specific bar count
[visualizer on bars=48]

# Disable
[visualizer off]

# Toggle
[visualizer toggle]
```

The visualizer renders frequency bars over the scene. It's disabled by default and intended for rhythm-game-style scenes or aesthetic overlays.

---

## Scene Examples

### Example 1: Atmospheric Scene Change

```vns
@scenario atmosphere_demo
@character narrator "Narrator"
@background forest assets/backgrounds/forest_day.png
@background cave assets/backgrounds/dark_cave.png

@label start
[bg forest]
[bgm assets/audio/bgm/forest_birds.ogg]
narrator: The forest was peaceful.

[wait 500]
[bgm_fadeout 1500]
[transition FADE 1200 cave]
[wait 200]
[bgm assets/audio/bgm/cave_drip.ogg]
[volume bgm 0.4]
narrator: The cave was eerily quiet.
[end]
```

### Example 2: Battle Sequence with Audio

```vns
@label battle_start
[bgm_crossfade assets/audio/bgm/battle_intense.ogg 800]
[volume bgm 0.8]
[sfx assets/audio/sfx/battle_start.ogg]
[screen shake 6 400]

narrator: The enemy attacks!

[sfx assets/audio/sfx/sword_clash.ogg]
[wait 300]
[sfx assets/audio/sfx/hit_impact.ogg]
[screen flash 0.5 150 255 200 200]

hero: Take this!

@label battle_won
[sfx assets/audio/sfx/victory_chime.ogg]
[bgm_crossfade assets/audio/bgm/victory_fanfare.ogg 1200 false]
narrator: Victory!
[wait 3000]
[bgm_crossfade assets/audio/bgm/calm_theme.ogg 2000]
```

### Example 3: Voice Acting with BGM

```vns
@label voiced_scene
[bgm assets/audio/bgm/emotional.ogg]
[volume bgm 0.3]  # lower BGM so voice is clear
[volume voice 1.0]

[voice assets/audio/voices/hero/confession_01.ogg]
hero: I've been meaning to tell you something.

[voice assets/audio/voices/hero/confession_02.ogg]
hero: All this time, I...

[voice_stop]
[volume bgm 0.7]  # bring BGM back up
narrator: The words hung in the air.
```

### Example 4: Menu Music Loop

```vns
@label menu_music
[bgm assets/audio/bgm/title_theme.ogg]
[volume bgm 0.7]

# When player starts the game:
@label start_game
[bgm_fadeout 1000]
[wait 1200]
[bgm assets/audio/bgm/prologue.ogg]
```

---

## Audio File Conventions

Recommended project layout:

```text
assets/audio/
  bgm/          # background music tracks
    main_theme.ogg
    battle.ogg
    calm.ogg
  sfx/           # sound effects
    door_open.ogg
    explosion.ogg
    ui_click.ogg
  voices/        # voice clips
    hero/
      line_001.ogg
      line_002.ogg
    narrator/
      intro.ogg
```

Supported formats depend on the audio backend:
- **Simp3** (default): MP3, OGG, WAV
- **FX**: formats supported by JavaFX Media

Use compressed formats (OGG, MP3) for long tracks and WAV for short SFX when latency matters.

---

## Audio Backend Selection

At runtime launch:

```bash
./gradlew :runtime:run --args='--audio auto'    # tries Simp3 first, falls back to FX
./gradlew :runtime:run --args='--audio simp3'   # force Simp3
./gradlew :runtime:run --args='--audio fx'      # force JavaFX
```

---

## Performance Tips

- Keep simultaneous audio channels intentional (BGM + one SFX + one voice is typical).
- Normalize volumes in your audio files to avoid heavy runtime gain swings.
- Use `[bgm_fadeout]` before `[bgm]` for clean transitions instead of abrupt stops.
- Prefer `[bgm_crossfade]` for seamless music changes.

---

## Related Docs

- [VNS Commands Reference](vns-commands.md)
- [VNS Overview](vns-scripting.md)
- [Transitions & Screen Effects](vns-transitions.md)
