# JVN Cookbook & Recipes

Practical patterns and end-to-end examples for common VNS development tasks.

---

## Recipe 1: Visual Novel with Multiple Routes

A classic branching VN structure with a common start, diverging routes, and route-specific endings.

### Project structure

```text
scripts/story/
├── prologue.vns
├── route_a.vns
├── route_b.vns
└── common/
    └── characters.vns
```

### `common/characters.vns`

```vns
@character narrator "Narrator"
@character hero "Yuki"
@character friend "Sakura"
@character rival "Takeshi"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero happy assets/characters/yuki/happy.png
@charimg hero sad assets/characters/yuki/sad.png
@charimg friend neutral assets/characters/sakura/neutral.png
@charimg friend happy assets/characters/sakura/happy.png
@charimg rival neutral assets/characters/takeshi/neutral.png
@charimg rival smug assets/characters/takeshi/smug.png

@background school assets/backgrounds/school_hallway.png
@background park assets/backgrounds/park_bench.png
@background sunset assets/backgrounds/sunset_hill.png
```

### `prologue.vns`

```vns
@scenario prologue
@include common/characters.vns

@var affinity_sakura = 0
@var affinity_takeshi = 0

@label start
[bg school]
[bgm assets/audio/bgm/school_day.ogg]
[show hero center neutral]

narrator: It was the first day of the new semester.

[show friend left happy]
friend: Yuki! Over here!

[show hero center happy]
hero: Sakura! Long time no see.

[show rival right smug]
rival: Well well, if it isn't the dynamic duo.

narrator: The three of them stood in the hallway, just like old times.

> Greet Sakura warmly -> warm_sakura
> Challenge Takeshi -> challenge_takeshi
> Stay neutral -> stay_neutral

@label warm_sakura
[inc affinity_sakura 2]
hero: Sakura, I've been looking forward to seeing you.
[show friend left happy]
friend: Me too! Let's walk together.
[jump afternoon]

@label challenge_takeshi
[inc affinity_takeshi 2]
hero: Takeshi, ready to lose at kendo again?
rival: You wish. This year is my year.
[jump afternoon]

@label stay_neutral
[inc affinity_sakura 1]
[inc affinity_takeshi 1]
hero: It's good to see both of you.
[jump afternoon]

@label afternoon
[transition FADE 800 park]
[bgm_crossfade assets/audio/bgm/afternoon.ogg 1500]
narrator: After classes, they gathered at the park.

# More story events that adjust affinity...

@label route_split
[if affinity_sakura > affinity_takeshi goto route_a_start]
[if affinity_takeshi > affinity_sakura goto route_b_start]
# Tie-breaker: one final choice
> Spend the evening with Sakura -> route_a_start
> Train with Takeshi -> route_b_start

@label route_a_start
[goto RouteA:start]

@label route_b_start
[goto RouteB:start]
```

### Timeline

```text
arc "Prologue" script "scripts/story/prologue.vns" entry "start" cluster "Main" at 40,80
arc "RouteA" script "scripts/story/route_a.vns" entry "start" cluster "Routes" at 320,40
arc "RouteB" script "scripts/story/route_b.vns" entry "start" cluster "Routes" at 320,160

link Prologue:route_a_start -> RouteA:start
link Prologue:route_b_start -> RouteB:start
```

---

## Recipe 2: RPG-Style Town Hub

A town with multiple locations the player can visit, with shops, NPCs, and quest tracking.

```vns
@scenario town
@character narrator "Narrator"
@character hero "Kai"
@character shopkeeper "Merchant"
@character elder "Village Elder"

@background town_square assets/backgrounds/town_square.png
@background shop assets/backgrounds/shop_interior.png
@background elder_house assets/backgrounds/elder_house.png

@var gold = 200
@var potions = 1
@var has_quest = false
@var quest_complete = false
@var sword_bought = false

@label hub
[bg town_square]
[bgm assets/audio/bgm/town.ogg]
narrator: You stand in the village square. Gold: ${gold}

> Visit the shop -> shop
> Visit the elder [if !quest_complete] -> elder
> Rest at the inn (free) -> inn
> Leave town [if quest_complete] -> depart

@label shop
[transition FADE 400 shop]
[show shopkeeper center neutral]
shopkeeper: Welcome! What can I do for you?

> Buy potion — 30g ({potions, plural, one{# owned} other{# owned}}) [if gold >= 30] -> buy_potion
> Buy sword — 150g [if gold >= 150 && !sword_bought] -> buy_sword
> Sell herbs — +20g [if has_herbs] -> sell_herbs
> Leave -> shop_exit

@label buy_potion
[dec gold 30]
[inc potions]
[sfx assets/audio/sfx/coin.ogg]
shopkeeper: Here you go!
narrator: Potions: ${potions}, Gold: ${gold}
[jump shop]

@label buy_sword
[dec gold 150]
[flag sword_bought]
[sfx assets/audio/sfx/equip.ogg]
shopkeeper: A fine blade! Use it well.
narrator: Gold: ${gold}
[jump shop]

@label sell_herbs
[inc gold 20]
[unflag has_herbs]
[sfx assets/audio/sfx/coin.ogg]
shopkeeper: Thanks for the herbs!
[jump shop]

@label shop_exit
[hide shopkeeper]
[jump hub]

@label elder
[transition FADE 400 elder_house]
[show elder center neutral]

[if has_quest]
  elder: Did you clear the cave?
  [if quest_complete]
    elder: Thank you, hero! The village is safe.
    [inc gold 100]
    [sfx assets/audio/sfx/fanfare.ogg]
    narrator: Received 100 gold!
  [else]
    elder: Please hurry. The monsters grow stronger.
  [endif]
  [jump elder_exit]
[endif]

elder: Brave adventurer, monsters threaten our village.
elder: Will you clear the cave to the north?

> Accept the quest -> accept_quest
> Not yet -> elder_exit

@label accept_quest
[flag has_quest]
elder: Thank you! Be careful out there.
[jump elder_exit]

@label elder_exit
[hide elder]
[jump hub]

@label inn
narrator: You rest at the inn and recover your strength.
[wait 1000]
[jump hub]

@label depart
[bgm_fadeout 1500]
[transition FADE 1200]
narrator: With the quest complete, you set off for new horizons.
[end]
```

---

## Recipe 3: Voiced Dialogue with Audio Management

Managing BGM, SFX, and voice clips together for a cinematic experience.

```vns
@scenario voiced_scene
@character narrator "Narrator"
@character captain "Captain"
@character officer "First Officer"

@background bridge assets/backgrounds/ship_bridge.png

@label start
[bg bridge]
[bgm assets/audio/bgm/tension.ogg]
[volume bgm 0.4]

[show captain left neutral]
[show officer right neutral]

# Each dialogue line has a matching voice clip
[voice assets/audio/voices/captain/line_001.ogg]
captain: Status report, officer.

[voice assets/audio/voices/officer/line_001.ogg]
officer: Sensors detect an unknown vessel approaching.

[screen shake 3 200]
[sfx assets/audio/sfx/alert.ogg]

[voice assets/audio/voices/captain/line_002.ogg]
captain: Red alert. All hands to battle stations.

[bgm_crossfade assets/audio/bgm/battle_tension.ogg 1200]
[volume bgm 0.6]

[voice assets/audio/voices/officer/line_002.ogg]
officer: Weapons online, shields at maximum.

[screen shake 8 500]
[sfx assets/audio/sfx/explosion_distant.ogg]
[screen flash 0.4 200 255 200 150]

[voice assets/audio/voices/captain/line_003.ogg]
captain: {shake}Return fire!{/shake}

[sfx assets/audio/sfx/laser_fire.ogg]
[wait 300]
[sfx assets/audio/sfx/explosion.ogg]
[screen flash 0.6 150]

narrator: The battle had begun.
[end]
```

---

## Recipe 4: Layered Character Sprite System

A character with mix-and-match body parts for many expression combinations.

```vns
@scenario layered_demo
@character alice "Alice"

# Define individual layers
@charlayer alice base assets/characters/alice/layers/body.png
@charlayer alice eyes_neutral assets/characters/alice/layers/eyes_neutral.png
@charlayer alice eyes_happy assets/characters/alice/layers/eyes_happy.png
@charlayer alice eyes_angry assets/characters/alice/layers/eyes_angry.png
@charlayer alice eyes_sad assets/characters/alice/layers/eyes_sad.png
@charlayer alice mouth_neutral assets/characters/alice/layers/mouth_neutral.png
@charlayer alice mouth_smile assets/characters/alice/layers/mouth_smile.png
@charlayer alice mouth_frown assets/characters/alice/layers/mouth_frown.png
@charlayer alice mouth_open assets/characters/alice/layers/mouth_open.png
@charlayer alice blush assets/characters/alice/layers/blush.png
@charlayer alice glasses assets/characters/alice/layers/glasses.png

# Build named presets from layers
@charpreset alice neutral $base | $eyes_neutral | $mouth_neutral
@charpreset alice happy $base | $eyes_happy | $mouth_smile
@charpreset alice angry $base | $eyes_angry | $mouth_frown
@charpreset alice sad $base | $eyes_sad | $mouth_frown
@charpreset alice surprised $base | $eyes_happy | $mouth_open
@charpreset alice embarrassed $base | $eyes_sad | $mouth_smile | $blush
@charpreset alice studious $base | $eyes_neutral | $mouth_neutral | $glasses

# 7 expressions from 11 layers — combinatorial power!

@label start
[show alice center neutral]
alice: Hello, I'm Alice.

[show alice center happy]
alice: Nice to meet you!

[show alice center embarrassed]
alice: Oh, you're too kind...

[show alice center studious]
alice: Now, let's focus on the lesson.

[show alice center surprised]
alice: Wait, what was that?!

[show alice center angry]
alice: I told you not to do that!

[show alice center sad]
alice: I'm sorry... I didn't mean to yell.
[end]
```

---

## Recipe 5: Minigame Integration via JES

Launching a JES minigame from VNS and handling the results.

### VNS Script

```vns
@scenario minigame_flow
@character narrator "Narrator"
@character hero "Hero"

@var high_score = 0

@label start
narrator: Welcome to the arcade!

> Play the shooter game -> play_shooter
> Play the puzzle game -> play_puzzle
> Check high score -> check_score
> Leave -> leave

@label play_shooter
narrator: Loading shooter...
[jes push game/minigames/shooter.jes label after_shooter with difficulty=normal]

@label after_shooter
narrator: Shooter result: ${score} points, rank ${rank}.
[if score > high_score]
  [set high_score ${score}]
  narrator: {b}New high score!{/b}
  [sfx assets/audio/sfx/fanfare.ogg]
[endif]
[jump start]

@label play_puzzle
narrator: Loading puzzle...
[jes push game/minigames/puzzle.jes label after_puzzle with level=1]

@label after_puzzle
narrator: Puzzle result: ${score} points.
[if score > high_score]
  [set high_score ${score}]
  narrator: {b}New high score!{/b}
[endif]
[jump start]

@label check_score
narrator: Your high score is ${high_score}.
[jump start]

@label leave
narrator: See you next time!
[end]
```

### JES Minigame (returns results)

```jes
scene "Shooter" {
  // ... game entities and logic ...

  // When game ends:
  // call "return" { label: "after_shooter" score: 1500 rank: "S" }
}
```

---

## Recipe 6: Inline Timeline Animation

Using inline timeline blocks for cinematic moments without leaving VNS.

```vns
@scenario cinematic
@character narrator "Narrator"
@character hero "Hero"

@label dramatic_entrance
[bg throne_room]
[bgm assets/audio/bgm/epic.ogg]

# Hero slides in from off-screen with camera zoom
[show hero center neutral]
timeline {
  entity "hero" {
    0ms { x: -200, alpha: 0.0 }
    800ms { x: 640, alpha: 1.0, easing: ease_out }
  }
  cameraMove 0ms 0 0 1.0
  cameraMove 800ms 0 -30 0.92
  playAudio "assets/audio/sfx/whoosh.ogg"
}

[wait 200]
hero: I have arrived.

# Dramatic camera pull-back
timeline {
  cameraMove 0ms 0 -30 0.92
  cameraMove 600ms 0 0 1.0
}

narrator: The hero stood tall in the throne room.

# Screen shake for impact
[screen shake 8 400]
[sfx assets/audio/sfx/slam.ogg]
[screen flash 0.5 150]

hero: {shake}Let's end this!{/shake}
[end]
```

---

## Recipe 7: Localized / Gender-Aware Dialogue

Using ICU formatting for player-customizable text.

```vns
@scenario personalized
@character narrator "Narrator"

@var player_name = "Traveler"
@var gender = "other"
@var title = "adventurer"

@label start
narrator: What is your name?
# (In a real game, this would use a text input or preset choice)

> Alice (she/her) -> set_female
> Bob (he/him) -> set_male
> Sam (they/them) -> set_neutral

@label set_female
[set player_name "Alice"]
[set gender "female"]
[set title "heroine"]
[jump greet]

@label set_male
[set player_name "Bob"]
[set gender "male"]
[set title "hero"]
[jump greet]

@label set_neutral
[set player_name "Sam"]
[set gender "other"]
[set title "champion"]
[jump greet]

@label greet
narrator: Welcome, ${player_name} the ${title}!

narrator: {gender, select, male{He} female{She} other{They}} stood at the crossroads.

narrator: The wind tugged at {gender, select, male{his} female{her} other{their}} cloak.

narrator: "${player_name}," the old sage called. "You are the {title} we've been waiting for."

narrator: {gender, select, male{He} female{She} other{They}} nodded and stepped forward.

[end]
```

---

## Recipe 8: Custom Menu Screens

Setting up a main menu with custom extras/gallery/credits screens.

### `config/menu/registry/menu.registry`

```properties
defaultMenu=main
menus=main,load,save,settings,extras,credits
layouts=default
styles=default,accent
```

### `config/menu/menus/main.menu`

```properties
titleText=My Visual Novel
hintsText=Select: Enter    Back: Esc
layout=default
defaultItemStyle=default
items=new_game,continue,extras,credits,quit

item.new_game.label=New Game
item.new_game.action=run_script:scripts/story/prologue.vns

item.continue.label=Continue
item.continue.action=load_menu

item.extras.label=Extras
item.extras.action=open_menu
item.extras.target=extras

item.credits.label=Credits
item.credits.action=open_menu
item.credits.target=credits

item.quit.label=Quit
item.quit.action=quit
```

### `config/menu/menus/extras.menu`

```properties
titleText=Extras
layout=default
defaultItemStyle=default
items=gallery,music,back

item.gallery.label=CG Gallery
item.gallery.action=run_script:scripts/system/gallery.vns

item.music.label=Music Room
item.music.action=run_script:scripts/system/music_room.vns

item.back.label=Back
item.back.action=back
```

---

## Recipe 9: Save Checkpoint Pattern

Strategic save points that auto-save at chapter boundaries.

```vns
@scenario chapter2

@label start
# Auto-save at chapter start
[save]
[hud Chapter 2 — The Forest]

narrator: Chapter 2 begins.

# ... chapter content ...

@label midpoint
# Auto-save at midpoint
[save]
[hud Progress saved]

# ... more content ...

@label boss_fight
# Save before a difficult section
[save]
[hud Checkpoint saved]

narrator: The final challenge awaits.

# ... boss encounter ...

@label chapter_end
[save]
[hud Chapter 2 Complete!]
narrator: To be continued...
[goto Chapter3:start]
```

---

## Related Docs

- [VNS Scripting Guide](scripting/vns/vns-scripting.md) — complete language reference
- [Getting Started](getting-started.md) — first-time setup
- [Documentation Index](INDEX.md) — full docs map
