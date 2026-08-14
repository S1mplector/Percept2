# UI By Example — Settings, Save/Load, and Help

Build three production screen types using the same menu profile system: adjustable settings, save slots with previews, and readable help content.

**Difficulty:** Intermediate
**Time:** 30 minutes
**Concepts:** built-in setting IDs, `{value}`, slot templates, preview assets, static text blocks

---

## Settings Screen

```properties
# config/menu/menus/settings.menu
titleText=Settings
hintsText=Left/Right: Adjust    Esc: Back
layout=submenu
defaultItemStyle=submenu
wrapSelection=true

items=audio_header,bgm_volume,sfx_volume,voice_volume,text_header,text_speed,auto_play_delay,skip_unread,back

item.audio_header.label=Audio
item.audio_header.action=noop
item.audio_header.enabled=false
item.audio_header.renderAs=section

item.bgm_volume.label=Music Volume: {value}
item.sfx_volume.label=SFX Volume: {value}
item.voice_volume.label=Voice Volume: {value}

item.text_header.label=Reading
item.text_header.action=noop
item.text_header.enabled=false
item.text_header.renderAs=section

item.text_speed.label=Text Speed: {value}
item.auto_play_delay.label=Auto Advance: {value}
item.skip_unread.label=Skip Unread: {value}

item.back.label=Back
item.back.action=back
```

The runtime binds recognized item IDs to their setting. `{value}` is replaced dynamically. Other built-in IDs include `skip_after_choices`, `click_reveal_before_advance`, `input_profile`, and advanced physics settings.

---

## Save and Load Screens

Save-slot template items are repeated by the runtime:

```properties
# config/menu/menus/save.menu
titleText=Save Journey
hintsText=Enter: Save    Esc: Back    Del: Delete    R: Rename
layout=slots
defaultItemStyle=slot
items=new_slot,save_slot

item.new_slot.label=Create New Save
item.new_slot.style=submenu
item.new_slot.action=save_menu

item.save_slot.action=save_menu
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/save/empty.png
item.save_slot.slotPreviewFrameAsset=assets/ui/save/frame.png
item.save_slot.slotPreviewX=10
item.save_slot.slotPreviewY=5
item.save_slot.slotPreviewWidth=144
item.save_slot.slotPreviewHeight=81
```

```properties
# config/menu/menus/load.menu
titleText=Load Journey
hintsText=Enter: Load    Esc: Back    Del: Delete
layout=slots
defaultItemStyle=slot
items=save_slot

item.save_slot.action=load_menu
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/save/empty.png
item.save_slot.slotPreviewFrameAsset=assets/ui/save/frame.png
item.save_slot.slotPreviewX=10
item.save_slot.slotPreviewY=5
item.save_slot.slotPreviewWidth=144
item.save_slot.slotPreviewHeight=81
```

Preview geometry is pixel-based inside each item row. Configure all four preview measurements together.

Use a tall slot layout:

```properties
# config/menu/layouts/slots.layout
extends=submenu
listYStart=0.20
lineHeight=104
listWidthFactor=0.68
maxVisibleItems=5
```

---

## Help and Controls Screen

Static text blocks are regular disabled menu items with a render role:

```properties
# config/menu/menus/help.menu
titleText=Help
hintsText=Esc: Back
layout=submenu
defaultItemStyle=submenu
items=controls_header,controls_body,saves_header,saves_note,back

item.controls_header.label=Controls
item.controls_header.action=noop
item.controls_header.enabled=false
item.controls_header.renderAs=section
item.controls_header.fontWeight=BOLD

item.controls_body.label=Click or press Enter to advance. A toggles Auto, Ctrl/Cmd toggles Skip, and H hides the interface.
item.controls_body.action=noop
item.controls_body.enabled=false
item.controls_body.renderAs=body
item.controls_body.rowSpan=3
item.controls_body.bodyAlign=left
item.controls_body.bodyPaddingY=6

item.saves_header.label=Saving
item.saves_header.action=noop
item.saves_header.enabled=false
item.saves_header.renderAs=section

item.saves_note.label=Use the pause menu for named saves. Quick save is available from the dialogue controls.
item.saves_note.action=noop
item.saves_note.enabled=false
item.saves_note.renderAs=note
item.saves_note.rowSpan=3
item.saves_note.bodyAlign=left

item.back.label=Back
item.back.action=back
```

Use `section` for headings, `body` for paragraphs, and `note` for card-like callouts. Static blocks do not receive hover or selection.

---

## Register Everything

```properties
menus=main,settings,save,load,help
layouts=default,submenu,slots
styles=default,submenu,slot
```

Add routes from the main or pause menu and test each screen from its real entry point.

---

## Key Takeaways

1. Settings behavior is keyed by recognized item IDs and `{value}` labels.
2. Save/load screens use runtime-expanded slot template items.
3. Slot preview coordinates are pixels inside a row.
4. Help content uses non-interactive `section`, `body`, and `note` items.
5. Standard screens remain ordinary menu-profile sources and inherit the same navigation, layout, and style system.

---

## Next

Move from application menus into live story state with [Reactive Overlay Screens](09-reactive-overlay-screens.md).

[Back to UI By Example](../ui-by-example.md)
