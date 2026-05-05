# Help Screen Configuration

Canonical pattern for building Ren'Py-style help and controls screens in JVN using the regular menu system.

Model: `modules/core/src/main/java/com/jvn/core/menu/config/MenuScreenSpec.java`  
Renderer: `modules/fx/src/main/java/com/jvn/fx/menu/MenuRenderer.java`

---

## Overview

JVN help screens are regular `.menu` screens. The key difference is that most rows render as
static text blocks rather than selectable buttons.

Use:

- `renderAs=section` for headers
- `renderAs=body` for wrapped paragraphs
- `renderAs=note` for wrapped paragraphs inside a subtle card

This keeps help, controls, and explanatory pages inside the same layout/style system as the rest
of the menu stack.

---

## File Location

```text
config/menu/menus/help.menu
```

---

## Example Help Screen

```properties
titleText=Help
hintsText=Esc: Back
layout=submenu
defaultItemStyle=submenu
wrapSelection=true

items=controls_header,controls_body,save_header,save_body,editor_header,editor_note,back

item.controls_header.label=Controls
item.controls_header.action=noop
item.controls_header.enabled=false
item.controls_header.renderAs=section
item.controls_header.fontWeight=BOLD
item.controls_header.fontSize=18

item.controls_body.label=Click or press Enter to advance dialogue. Ctrl/Cmd toggles skip, A toggles auto mode, and H hides the interface.
item.controls_body.action=noop
item.controls_body.enabled=false
item.controls_body.renderAs=body
item.controls_body.rowSpan=3
item.controls_body.bodyAlign=left
item.controls_body.bodyPaddingY=6
item.controls_body.fontSize=16

item.save_header.label=Saving and Loading
item.save_header.action=noop
item.save_header.enabled=false
item.save_header.renderAs=section
item.save_header.fontWeight=BOLD
item.save_header.fontSize=18

item.save_body.label=F5 saves and F9 loads during gameplay. You can also use the themed save and load screens from the pause menu.
item.save_body.action=noop
item.save_body.enabled=false
item.save_body.renderAs=body
item.save_body.rowSpan=3
item.save_body.bodyAlign=left
item.save_body.fontSize=16

item.editor_header.label=Project Workflow
item.editor_header.action=noop
item.editor_header.enabled=false
item.editor_header.renderAs=section
item.editor_header.fontWeight=BOLD
item.editor_header.fontSize=18

item.editor_note.label=The project explorer opens scripts, timelines, layouts, and menu profiles. Layout and menu previews use the same runtime renderer.
item.editor_note.action=noop
item.editor_note.enabled=false
item.editor_note.renderAs=note
item.editor_note.rowSpan=4
item.editor_note.bodyAlign=left
item.editor_note.bodyPaddingX=18
item.editor_note.bodyPaddingY=10
item.editor_note.fontSize=16

item.back.label=Back
item.back.style=submenu
item.back.action=back
```

---

## Authoring Notes

- Use `rowSpan` or `rows` to give longer paragraphs enough height in auto-laid-out menus.
- Use explicit `boundsX/Y/Width/Height` if you want magazine-style layouts instead of stacked rows.
- `renderAs=note` is useful for warnings, tips, or editor-specific guidance that should read like a callout.
- Title and footer placement still come from the regular menu layout keys:
  - `titleAlign`
  - `titleX`
  - `hintsAlign`
  - `hintsX`

---

## Relation To Ren'Py

This is the JVN equivalent of teams placing large explanatory text inside custom `help` screens in
`screens.rpy`, but without introducing a second UI system. The same menu profile, layout, and style
pipeline stays in control.
