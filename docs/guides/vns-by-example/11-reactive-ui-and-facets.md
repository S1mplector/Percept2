# VNS By Example — Reactive UI with Facets

Build a story-facing companion card whose text, portrait, meter, visibility, and available actions come from live VNS variables.

**Difficulty:** Advanced
**Time:** 20 minutes
**Concepts:** `.facet` files, `[screen show]`, `[screen call]`, live variable binding, `visibleIf`, conditional buttons, return values

---

## What You Are Building

The player can inspect a companion card at any time. A normal `show` displays the card without pausing the script; a modal `call` waits for the player and returns either `invite` or `leave`.

```text
config/
└── facets/
    └── companion_card.facet
game/
└── story/
    └── companion-demo.vns
```

Facets are useful when dialogue choices are too limited and a full menu is too rigid. They remain part of the standard overlay-screen system, so existing screen commands, conditions, buttons, and return values still apply.

---

## 1. Create the Facet

Create `config/facets/companion_card.facet`:

```properties
id=companion_card
title=Companion
x=0.18
y=0.14
width=0.64
height=0.58
modal=true
dim=true
dismiss=false
call=true
returnKey=screen.return.companion_card

nodes=content,portrait,name,relationship_label,relationship,warning

# Groups establish a coordinate space for their children.
# Declare a parent before any node that refers to it.
node.content.type=group
node.content.x=0.06
node.content.y=0.17
node.content.width=0.88
node.content.height=0.60

node.portrait.type=image
node.portrait.parent=content
node.portrait.value=${companion_portrait}
node.portrait.x=0.00
node.portrait.y=0.00
node.portrait.width=0.27
node.portrait.height=0.88

node.name.type=text
node.name.parent=content
node.name.text=${companion_name}
node.name.x=0.33
node.name.y=0.00
node.name.width=0.67
node.name.height=0.20

node.relationship_label.type=text
node.relationship_label.parent=content
node.relationship_label.text=Relationship: ${relationship_points}
node.relationship_label.x=0.33
node.relationship_label.y=0.28
node.relationship_label.width=0.67
node.relationship_label.height=0.14

node.relationship.type=bar
node.relationship.parent=content
node.relationship.value=${relationship_ratio}
node.relationship.x=0.33
node.relationship.y=0.48
node.relationship.width=0.67
node.relationship.height=0.10

node.warning.type=text
node.warning.parent=content
node.warning.text=Spend more time together before inviting this companion.
node.warning.visibleIf=relationship_ratio < 0.50
node.warning.x=0.33
node.warning.y=0.68
node.warning.width=0.67
node.warning.height=0.18

buttons=invite,leave

button.invite.label=Invite
button.invite.action=return
button.invite.target=invite
button.invite.enabledIf=relationship_ratio >= 0.50

button.leave.label=Close
button.leave.action=return
button.leave.target=leave
```

The `nodes` list controls paint order. The group comes first, followed by its children. Node coordinates are normalized to their parent: `0.0` is the near edge and `1.0` is the far edge.

---

## 2. Drive It from VNS

Create `game/story/companion-demo.vns`:

```vns
@scenario companion_demo

@character mc "Mara"
@character companion "Iris"

@background lounge "assets/backgrounds/lounge.png"
@charimg mc neutral "assets/characters/mara/neutral.png"
@charimg companion calm "assets/characters/iris/calm.png"

@var relationship_points = 62
@var relationship_ratio = 0.62
@var companion_name = "Iris"
@var companion_portrait = "assets/characters/iris/portrait.png"

@label start
[bg lounge]
[show mc left neutral]
[show companion right calm]

companion: We still have an hour before the train.

# A non-blocking preview. The script continues immediately.
[screen show companion_card]
mc: Let me check our plan.
[screen hide companion_card]

# A modal call waits until a Facet button returns a value.
[screen call companion_card]

[if screen.return.companion_card == "invite"]
  mc: Want to explore the old station with me?
  companion: I thought you'd never ask.
  [inc relationship_points 5]
  [set relationship_ratio 0.67]
[else]
  mc: We should probably stay near the platform.
[endif]

[end]
```

The file ID is the filename without `.facet`. Therefore `[screen call companion_card]` discovers `config/facets/companion_card.facet` automatically.

---

## `show` Versus `call`

| Command | Script behavior | Typical use |
|---|---|---|
| `[screen show id]` | Displays the overlay and continues | HUD, status preview, passive information |
| `[screen call id]` | Displays the overlay and waits for a return action | Confirmation, selection, shop, character card |
| `[screen hide id]` | Removes a shown overlay | Closing a passive overlay from script |

For a called Facet, give at least one button `action=return`. Its `target` becomes the value in `screen.return.<id>` unless a different `returnKey` is configured.

---

## Live Binding

Facet content resolves against the current VN state whenever the overlay renders:

```vns
[set relationship_points 80]
[set relationship_ratio 0.80]
[set companion_portrait "assets/characters/iris/happy-portrait.png"]
[screen show companion_card]
```

You do not need to regenerate the `.facet` file. Text and image values interpolate `${variables}`, bars consume numeric values, and `visibleIf` or `enabledIf` expressions react to the same state.

Keep the two relationship variables in this example synchronized deliberately: one is presentation text (`62`), while the other is the normalized bar value (`0.62`). In a larger game, update both in one shared VNS subroutine.

---

## Extend the Pattern

Once the basic card works, the same structure can become:

- a quest summary with one group per objective;
- a chapter card whose artwork changes with story state;
- a compact HUD opened with `[screen show status]`;
- a confirmation panel with conditional buttons;
- an inventory summary that returns the selected next action.

Facet visual nodes are presentational. Put interaction in the standard overlay buttons rather than expecting an image, text, or bar node to receive clicks.

---

## Troubleshooting

| Symptom | Check |
|---|---|
| The wrong screen opens | A file earlier in screen discovery may share the same ID; keep Facet IDs unique |
| A child node is missing | Its parent must appear earlier in the `nodes` list |
| A button never enables | Inspect the VNS variable name, type, and `enabledIf` expression |
| The call never resumes | At least one reachable button must use `action=return` |
| An image is blank | Use a project-relative asset path and verify exact filename case |
| A bar looks empty | Supply a numeric value in the range expected by your Facet design |

---

## Key Takeaways

1. Put reusable reactive layouts in `config/facets/<id>.facet`.
2. Use `${variable}` and conditions to bind the layout to live VN state.
3. Use `show` for non-blocking information and `call` for a modal result.
4. Read a returned button target from `screen.return.<id>`.
5. Declare group parents before their children and treat `nodes` as paint order.
6. Keep node visuals separate from standard overlay-button interaction.

---

## Next

Continue to [Phone Storytelling](12-phone-storytelling.md), or dive into the complete [JVN Facets reference](../../scripting/ui/facets.md).

[Back to VNS By Example](../vns-by-example.md)
