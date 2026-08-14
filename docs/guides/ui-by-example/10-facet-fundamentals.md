# UI By Example — Facet Fundamentals

Build a nested companion card from reusable groups, live text, a portrait, and a relationship meter.

**Difficulty:** Intermediate
**Time:** 25 minutes
**Concepts:** `.facet`, node order, parent geometry, `group`, `text`, `image`, `bar`, overlay buttons

---

## Create the Facet

```properties
# config/facets/companion_card.facet
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

nodes=content,portrait,details,name,relationship_label,relationship

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

node.details.type=group
node.details.parent=content
node.details.x=0.33
node.details.y=0.00
node.details.width=0.67
node.details.height=0.88

node.name.type=text
node.name.parent=details
node.name.text=${companion_name}
node.name.x=0.00
node.name.y=0.00
node.name.width=1.00
node.name.height=0.20

node.relationship_label.type=text
node.relationship_label.parent=details
node.relationship_label.text=Relationship: ${relationship_points}
node.relationship_label.x=0.00
node.relationship_label.y=0.34
node.relationship_label.width=1.00
node.relationship_label.height=0.16

node.relationship.type=bar
node.relationship.parent=details
node.relationship.value=${relationship_ratio}
node.relationship.x=0.00
node.relationship.y=0.56
node.relationship.width=1.00
node.relationship.height=0.12

buttons=invite,close

button.invite.label=Invite
button.invite.action=return
button.invite.target=invite
button.invite.enabledIf=relationship_ratio >= 0.50

button.close.label=Close
button.close.action=return
button.close.target=close
```

---

## Supply Live State

```vns
[set companion_name "Iris"]
[set companion_portrait "assets/characters/iris/portrait.png"]
[set relationship_points 72]
[set relationship_ratio 0.72]

[screen call companion_card]
[if screen.return.companion_card == "invite" goto invite_iris]
[jump continue_story]
```

Facet nodes resolve against the current VN state whenever the overlay renders. The file remains reusable; each call site supplies variables.

---

## Node Types

| Type | Purpose | Main content field |
|---|---|---|
| `group` | Nested coordinate container | none |
| `text` | Bound or literal text | `text` |
| `image` | Project asset | `value` |
| `bar` | Numeric meter | `value` |

Nodes are visual. Interaction belongs to the standard overlay buttons declared outside the node tree.

---

## Geometry and Paint Order

Facet coordinates are normalized inside the parent. A root node uses the Facet content area; a child uses its group.

The `nodes=` list has two jobs:

1. It declares which nodes exist.
2. It defines paint order.

Declare a parent before any child that names it. Put background artwork earlier than text or meters that should appear above it.

---

## Facet or Reactive Screen?

| Requirement | Use |
|---|---|
| One title and one body | Reactive screen |
| Several text/image/meter regions | Facet |
| Nested coordinate groups | Facet |
| Standard modal result buttons | Either |
| Menu-row keyboard navigation | Menu profile |

Facets extend the reactive screen lifecycle rather than creating a second overlay stack.

---

## Key Takeaways

1. Put Facets in `config/facets/<id>.facet`.
2. Compose layouts from ordered `group`, `text`, `image`, and `bar` nodes.
3. Child coordinates are normalized within their parent.
4. Bind content with `${variables}` and keep interaction in overlay buttons.
5. Use `screen.return.<id>` to consume a modal Facet result.

---

## Next

Add conditional composition, localization, timers, and reusable staging in [Advanced Facets](11-advanced-facets.md).

[Back to UI By Example](../ui-by-example.md)
