# UI By Example — Phone, Gallery, and Music Room Surfaces

Configure JVN's specialized interface surfaces instead of rebuilding them from generic controls.

**Difficulty:** Advanced
**Time:** 30 minutes
**Concepts:** phone seed data and skins, story mutations, gallery registries, music-room registries, persistent unlocks

---

## Seed and Skin the Story Phone

```properties
# config/phone/phone.properties
home.mode=apps
status.time=08:14
status.signal=5G
status.battery=86

app.skin=sms
app.skin.background=assets/ui/phone/background.png
app.skin.topBar=assets/ui/phone/top-bar.png
app.skin.bottomBar=assets/ui/phone/bottom-bar.png
app.skin.messageField=assets/ui/phone/message-field.png
app.skin.statusBackdrop=assets/ui/phone/status-backdrop.png
app.skin.bubbleIncoming=assets/ui/phone/bubble-incoming.png
app.skin.bubbleOutgoing=assets/ui/phone/bubble-outgoing.png

contacts=mc,iris
contact.mc.name=Mara
contact.mc.self=true
contact.iris.name=Iris
contact.iris.avatar=assets/phone/iris.png

chats=mc_iris
chat.mc_iris.title=Iris
chat.mc_iris.participants=mc,iris
chat.mc_iris.icon=assets/phone/iris.png
chat.mc_iris.composerHint=Message

home.apps=messages
phoneapp.messages.title=Messages
phoneapp.messages.icon=assets/ui/phone/apps/messages.png
phoneapp.messages.badge=0
phoneapp.messages.page=0
phoneapp.messages.target=chat
phoneapp.messages.targetValue=mc_iris
```

Seed reusable contacts, navigation, and skin assets here. Add story events from VNS:

```vns
[phone message mc_iris iris "You need to see this." time=08:14]
[phone message mc_iris type=image asset="assets/phone/clue.png" caption="Under the east clock." time=08:15]
[phone unread mc_iris true]
[phone open chat mc_iris]
[phone unread mc_iris false]
```

Phone mutations live in VN state, so save/load and rollback testing are part of UI validation.

---

## Configure a CG Gallery

```properties
# config/gallery/gallery.properties
entry.ids=clock_clue,station_reunion,ending_signal

entry.clock_clue.image=assets/cg/clock-clue.png
entry.clock_clue.category=Chapter 1
entry.clock_clue.order=0

entry.station_reunion.image=assets/cg/station-reunion.png
entry.station_reunion.category=Chapter 3
entry.station_reunion.order=0

entry.ending_signal.image=assets/cg/ending-signal.png
entry.ending_signal.category=Endings
entry.ending_signal.order=0
entry.ending_signal.unlockFlag=ending.signal.seen
```

Without an explicit flag, the default is `gallery.unlocked.<id>`. Unlock a custom flag from VNS:

```vns
[persistent flag ending.signal.seen]
```

Open the built-in gallery from a menu:

```properties
item.gallery.label=CG Gallery
item.gallery.action=gallery
```

---

## Configure a Music Room

```properties
# config/gallery/music-room.properties
track.ids=platform_theme,signal_theme,credits

track.platform_theme.audio=assets/audio/bgm/platform-theme.ogg
track.platform_theme.title=Empty Platform
track.platform_theme.artist=JVN Composer
track.platform_theme.category=BGM
track.platform_theme.order=0

track.signal_theme.audio=assets/audio/bgm/signal-theme.ogg
track.signal_theme.title=The Signal
track.signal_theme.artist=JVN Composer
track.signal_theme.category=BGM
track.signal_theme.order=1

track.credits.audio=assets/audio/bgm/credits.ogg
track.credits.title=Last Train Home
track.credits.artist=JVN Composer
track.credits.category=Ending
track.credits.order=0
track.credits.unlockFlag=ending.credits.heard
```

The default unlock key is `music.unlocked.<id>`. Open the surface with:

```properties
item.music.label=Music Room
item.music.action=music_room
```

---

## Why Use Specialized Surfaces?

These runtimes already own domain behavior: phone navigation and messages, CG categories and unlocks, audio playback and track metadata. Rebuilding them from generic menus would duplicate state management and input behavior.

Use menu profiles to link to the surfaces and their dedicated configuration to supply content.

---

## Key Takeaways

1. Seed stable phone data and skin assets in `phone.properties`.
2. Use VNS phone commands for events that occur during the story.
3. Gallery and music-room registries group ordered entries by category.
4. Persistent flags control unlock state across save slots.
5. Open gallery and music room through their built-in menu actions.

---

## Next

Make every surface resilient in [Localization, Accessibility, and Responsive Layout](14-localization-accessibility-and-responsive-ui.md).

[Back to UI By Example](../ui-by-example.md)
