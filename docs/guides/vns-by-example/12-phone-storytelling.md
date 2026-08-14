# VNS By Example — Phone Storytelling

Use the built-in phone overlay to stage contacts, message threads, unread state, media, and calls as part of a VNS scene.

**Difficulty:** Advanced
**Time:** 20 minutes
**Concepts:** phone seed data, runtime mutations, chats, media messages, unread state, phone calls, save and rollback behavior

---

## What You Are Building

During a station scene, Iris sends the protagonist a clue. The script adds a message and photo, marks the thread unread, opens the conversation, and then offers an ordinary VNS choice about what to do next.

```text
config/
└── phone/
    └── phone.properties
game/
└── story/
    └── phone-demo.vns
assets/
└── phone/
    ├── iris.png
    └── platform-clue.png
```

Seed stable contacts and app structure in properties. Use VNS commands for events that happen during the story.

---

## 1. Seed the Phone

Create `config/phone/phone.properties`:

```properties
home.mode=apps
status.time=08:14
status.signal=5G
status.battery=86

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

calls=iris_video
call.iris_video.title=Iris
call.iris_video.subtitle=Incoming video call
call.iris_video.participant=iris
call.iris_video.video=true
```

The identifiers `iris`, `mc_iris`, and `iris_video` are stable handles used by VNS. They do not have to match the display names shown to the player.

---

## 2. Add Story-Time Messages

Create `game/story/phone-demo.vns`:

```vns
@scenario phone_demo

@character mc "Mara"
@character iris "Iris"

@background platform "assets/backgrounds/station-platform.png"
@charimg mc neutral "assets/characters/mara/neutral.png"
@charimg mc worried "assets/characters/mara/worried.png"

@label start
[bg platform]
[show mc center neutral]
[sfx "assets/audio/sfx/phone-buzz.ogg"]

# These commands mutate the phone model before it opens.
[phone message mc_iris iris "You need to see this." time=08:14]
[phone message mc_iris type=image asset="assets/phone/platform-clue.png" caption="Under the east clock." time=08:15]
[phone unread mc_iris true]

mc: A message from Iris?

# Open directly to the populated thread.
[phone open chat mc_iris]

mc: That symbol was in Grandfather's notebook.
[show mc center worried]

> Call Iris now
  [phone call iris_video]
  iris: Don't touch anything until I get there.
  [jump investigate]
> Search the platform first
  mc: One quick look. Then I'll call her.
  [jump investigate]

@label investigate
[phone unread mc_iris false]
mc: The east clock is just beyond the ticket hall.
[end]
```

Phone content is part of VN state. Save/load and rollback preserve story-time mutations instead of rebuilding the phone from scratch.

---

## Seed Data Versus VNS Mutations

| Put in `phone.properties` | Put in the story script |
|---|---|
| Stable contacts and self-contact | A contact discovered or renamed during play |
| Initial threads and participants | Messages arriving in the current scene |
| App icons, targets, and home-page placement | Badge and unread changes |
| Reusable call definitions | A call opened by a story beat |
| Default status-bar appearance | Temporary story-specific content |

Missing contacts and threads can be created by commands, but seeding shared structure keeps IDs and assets reviewable in one place.

---

## Useful Phone Commands

### Open and close surfaces

```vns
[phone open]
[phone open home]
[phone open chat mc_iris]
[phone open call iris_video]
[phone chat mc_iris]
[phone call iris_video]
[phone close]
```

`[phone chat ...]` and `[phone call ...]` are convenient shorthands. The explicit `[phone open ...]` form reads better when several surface types appear together.

### Define or update records

```vns
[phone contact iris name="Iris" avatar="assets/phone/iris.png"]
[phone thread mc_iris title="Iris" participants=mc,iris icon="assets/phone/iris.png" composerHint="Message"]
[phone app messages title="Messages" icon="assets/ui/phone/apps/messages.png" badge=2 page=0 chat=mc_iris]
[phone call iris_video title="Iris" subtitle="Incoming video call" participant=iris video=true]
```

When `[phone call <id>]` has no options, it opens the existing call. Add options when defining or changing the call record; use `open=true` if the same command should also open it.

### Add message types

```vns
[phone message mc_iris iris "You awake?" time=08:14]
[phone message mc_iris type=image asset="assets/phone/clue.png" caption="Look closer" time=08:15]
[phone message mc_iris type=audio asset="assets/audio/voice/iris-note.ogg" caption="Voice note" duration=0:12]
[phone message mc_iris type=date "Friday, 8 March"]
[phone message mc_iris iris "Choose one" menu="Meet now|Later"]
[phone unread mc_iris true]
[phone clear mc_iris]
```

Use quoted values when text contains spaces. Keep IDs simple—lowercase letters, digits, and underscores make long scripts easier to scan.

---

## A Reliable Story Pattern

Phone scenes are easiest to maintain when each beat follows the same order:

1. Mutate the phone model: add the message, image, badge, or call.
2. Signal the event in the VN scene with sound, dialogue, or animation.
3. Open the exact surface the player needs.
4. Close or leave the phone, then continue story routing in VNS.
5. Clear unread state only when the story considers the content acknowledged.

Use normal VNS choices when the result must control labels or variables explicitly. Phone message menus are best treated as content inside the phone experience unless your project has additional Java integration for their results.

---

## Asset and Content Guidelines

- Use project-relative paths such as `assets/phone/iris.png`.
- Keep avatars small and consistently framed; the phone UI crops them into multiple contexts.
- Give media a caption when the visual alone may be ambiguous.
- Add timestamps deliberately. A coherent message chronology makes the interface feel authored rather than generated.
- Seed a self-contact with `self=true` so outgoing messages have a stable identity.
- Test direct opens, home navigation, save/load, and rollback—not only the first scripted appearance.

---

## Troubleshooting

| Symptom | Check |
|---|---|
| A thread has the wrong people | Verify its comma-separated `participants` IDs exist |
| An avatar or media item is blank | Check the project-relative path and filename case |
| The app does not open the expected chat | Verify `target=chat` and `targetValue=<thread-id>` |
| A call only updates data | Use an existing call with `[phone call id]`, or add `open=true` while mutating it |
| Old messages remain during repeated testing | Start a new game or use `[phone clear thread_id]` in a test-only path |
| An unread marker persists | Clear it explicitly with `[phone unread thread_id false]` |

---

## Key Takeaways

1. Seed reusable phone structure in `config/phone/phone.properties`.
2. Use VNS commands for messages and changes that happen during the story.
3. Open a precise chat or call so the player lands on the relevant content.
4. Treat phone state as VN state: include it in save/load and rollback testing.
5. Keep story branching in ordinary VNS choices unless custom integration exposes a phone result.
6. Use stable IDs independently from player-facing names.

---

## Where to Go Next

You have completed the VNS By Example path. Continue with the full [VNS Interop reference](../../scripting/vns/integration/vns-interop.md#phone-commands), revisit [Reactive UI with Facets](11-reactive-ui-and-facets.md), or build a gameplay segment with [JES By Example](../jes-by-example.md).

[Back to VNS By Example](../vns-by-example.md)
