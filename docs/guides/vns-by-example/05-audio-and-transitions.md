# VNS By Example — Audio and Transitions

Set the mood with background music, sound effects, voice clips, and visual transitions between scenes.

**Difficulty:** Intermediate
**Time:** 15 minutes
**Concepts:** `[bgm]`, `[sfx]`, `[voice]`, `[bgm_stop]`, `[bgm_fadeout]`, `[bgm_crossfade]`, `[transition]`, volume control

---

## The Script

```vns
@scenario audio_demo
@character narrator ""
@character hero "Yuki"

@background school assets/backgrounds/school.png
@background sunset assets/backgrounds/sunset_hill.png
@background night assets/backgrounds/night_sky.png

@label start
[bgm assets/audio/bgm/morning.ogg]
[bg school]

narrator: A peaceful morning at school.
[sfx assets/audio/sfx/bell.ogg]
hero: The bell already? Time flies.

[wait 500]
[bgm_crossfade assets/audio/bgm/evening.ogg 2000]
[transition DISSOLVE 1200 sunset]

narrator: The afternoon passed in a blink.
hero: What a beautiful sunset.

[sfx assets/audio/sfx/wind.ogg]
[wait 1000]

[bgm_fadeout 3000]
[transition FADE 1500 night]

narrator: Night fell gently over the town.
[bgm assets/audio/bgm/night_calm.ogg vol=0.4]

hero: Time to head home.
[bgm_stop]
[end]
```

---

## Background Music (`[bgm]`)

```vns
[bgm path/to/track.ogg]
[bgm path/to/track.ogg loop=false]
[bgm path/to/track.ogg vol=0.6]
[bgm path/to/track.ogg loop=true vol=0.8]
```

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `loop` | `true`/`false`/`on`/`off` | `true` | Loop the track |
| `vol` / `volume` | `0.0`–`1.0` | current | Set volume |

Only **one BGM** plays at a time. Starting a new BGM stops the previous one.

### BGM Control Commands

| Command | Description |
|---------|-------------|
| `[bgm_stop]` | Stop BGM immediately |
| `[bgm_fadeout 2000]` | Fade out over 2 seconds |
| `[bgm_pause]` | Pause playback |
| `[bgm_resume]` | Resume paused playback |
| `[bgm_seek 30.5]` | Seek to position (seconds) |
| `[bgm_crossfade track ms]` | Crossfade to new track over duration |

### Crossfade

Smoothly transition between two BGM tracks:

```vns
[bgm assets/audio/bgm/calm.ogg]
# ... scene plays ...
[bgm_crossfade assets/audio/bgm/tense.ogg 1500]
```

The old track fades out while the new one fades in over the specified duration.

---

## Sound Effects (`[sfx]`)

One-shot sounds that play alongside BGM:

```vns
[sfx assets/audio/sfx/door_open.ogg]
[sfx assets/audio/sfx/explosion.ogg]
[sfx assets/audio/sfx/footsteps.ogg]
```

| Command | Description |
|---------|-------------|
| `[sfx path]` | Play a sound effect |
| `[sfx_stop]` | Stop current SFX |

SFX are fire-and-forget — they play once and stop automatically.

---

## Voice (`[voice]`)

Play character voice clips:

```vns
[voice assets/audio/voices/hero_line_42.ogg]
hero: I won't give up!
```

| Command | Description |
|---------|-------------|
| `[voice path]` | Play a voice clip |
| `[voice_stop]` | Stop current voice |

Voice clips are typically synced with dialogue lines.

---

## Volume Control

Adjust channel volumes at runtime:

```vns
[volume bgm 0.5]       # background music at 50%
[volume sfx 0.8]       # sound effects at 80%
[volume voice 1.0]     # voice at full volume
```

---

## Global Audio Control

| Command | Description |
|---------|-------------|
| `[audio_stop_all]` | Stop all audio channels |
| `[audio_pause_all]` | Pause all channels |
| `[audio_resume_all]` | Resume all channels |

---

## Transitions (`[transition]`)

Visual transitions when changing backgrounds:

```vns
[transition TYPE duration]
[transition TYPE duration bgId]
```

| Type | Description |
|------|-------------|
| `FADE` | Fade to black, then to new scene |
| `DISSOLVE` | Gradually blend to new scene |
| `CROSSFADE` | Direct cross-dissolve |
| `SLIDE_LEFT` | Slide new scene in from right |
| `SLIDE_RIGHT` | Slide new scene in from left |
| `WIPE` | Wipe effect |

### With Background Change

```vns
[transition FADE 800 classroom]         # fade and switch bg
[transition DISSOLVE 1200 park]         # dissolve and switch bg
[transition CROSSFADE 1000 sunset]      # crossfade to sunset
[transition SLIDE_LEFT 600 next_room]   # slide in from right
```

### Without Background Change

```vns
[transition FADE 800]                   # just fade effect, same bg
```

---

## Patterns

### Scene Change with Audio

```vns
@label enter_forest
[bgm_crossfade assets/audio/bgm/forest.ogg 1500]
[transition DISSOLVE 1000 forest]
[sfx assets/audio/sfx/birds.ogg]
narrator: The forest was alive with sound.
```

### Dramatic Moment

```vns
[bgm_stop]
[wait 500]
narrator: ...
[sfx assets/audio/sfx/thunder.ogg]
[wait 200]
[bgm assets/audio/bgm/storm.ogg vol=0.7]
narrator: The storm arrived without warning.
```

### Emotional Scene

```vns
[bgm assets/audio/bgm/sad_piano.ogg vol=0.4]
[transition DISSOLVE 2000 rain]
[show hero center sad]
hero: I never got to say goodbye.
[wait 1000]
hero: Maybe some things are better left unsaid.
[bgm_fadeout 3000]
```

### Location Montage

```vns
[bgm assets/audio/bgm/upbeat.ogg]
narrator: And so the adventure began.

[transition SLIDE_LEFT 400 forest]
[sfx assets/audio/sfx/footsteps.ogg]
[wait 800]

[transition SLIDE_LEFT 400 mountains]
[sfx assets/audio/sfx/wind.ogg]
[wait 800]

[transition SLIDE_LEFT 400 castle]
[sfx assets/audio/sfx/trumpets.ogg]
narrator: At last, the castle!
```

### Quiet to Loud

```vns
[bgm assets/audio/bgm/ambient.ogg vol=0.2]
narrator: The cave was eerily quiet.

# Gradually increase tension
[volume bgm 0.4]
[wait 500]
narrator: Something moved in the darkness.

[volume bgm 0.7]
[sfx assets/audio/sfx/growl.ogg]
hero: What was that?!

[bgm_crossfade assets/audio/bgm/battle.ogg 500]
narrator: A monster appeared!
```

---

## Full Example: Coffee Shop Scene

```vns
@scenario coffee
@character narrator ""
@character hero "Yuki"
@character barista "Barista"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero happy assets/characters/yuki/happy.png
@charimg barista neutral assets/characters/barista/neutral.png
@charimg barista smile assets/characters/barista/smile.png

@background street assets/backgrounds/rainy_street.png
@background cafe assets/backgrounds/cafe_interior.png

@label start
[bgm assets/audio/bgm/rain_ambience.ogg vol=0.5]
[bg street]

narrator: Rain pattered against the sidewalk.
[sfx assets/audio/sfx/rain_heavy.ogg]
[show hero center neutral]
hero: I should find shelter.

[sfx assets/audio/sfx/door_bell.ogg]
[bgm_crossfade assets/audio/bgm/cafe_jazz.ogg 1500]
[transition DISSOLVE 800 cafe]

[show barista center smile]
barista: Welcome! Rough weather out there.

[show hero center happy]
hero: You have no idea.

barista: What can I get you?

> Hot chocolate
  [sfx assets/audio/sfx/pour.ogg]
  barista: One hot chocolate, coming right up!
  [jump enjoy]
> Black coffee
  [sfx assets/audio/sfx/pour.ogg]
  barista: Strong choice for a rainy day.
  [jump enjoy]

@label enjoy
[wait 500]
[volume bgm 0.3]
narrator: The warm drink and gentle jazz made the rain feel far away.
hero: This is nice.
[bgm_fadeout 3000]
[end]
```

---

## Key Takeaways

1. `[bgm path]` plays background music (loops by default, one track at a time)
2. `[sfx path]` plays one-shot sound effects
3. `[voice path]` plays voice clips synced with dialogue
4. `[bgm_crossfade]` smoothly transitions between BGM tracks
5. `[bgm_fadeout ms]` fades out the current track
6. `[volume channel level]` adjusts channel volumes at runtime
7. `[transition TYPE duration bgId]` combines visual transitions with background changes
8. Six transition types: `FADE`, `DISSOLVE`, `CROSSFADE`, `SLIDE_LEFT`, `SLIDE_RIGHT`, `WIPE`

---

## Next

- [Screen Effects and Timing](06-effects-and-timing.md) — shake, flash, wait, text speed, UI control
- [Back to Index](../vns-by-example.md)
