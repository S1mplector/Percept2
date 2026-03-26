# Phone Assets

Source: `editor/src/main/java/com/jvn/editor/ui/PhoneAssetsToolView.java`

The **Phone Assets** sidebar is the structured editor for the phone framework. It edits the same `phone.properties` data that runtime and preview consume, so contacts, threads, messages, wallpaper paths, and theme colors stay aligned with `[phone ...]` behavior.

It covers the full phone surface now used by runtime and preview: app chrome, status bar text, contacts, thread lists, home-grid apps, calls, and typed message payloads.

## What It Edits

- `config/phone/phone.properties`
- `game/config/phone/phone.properties` when that is the only existing phone config
- `.jvn/phone-assets-tool.properties` for editor-only panel state

## Core Workflow

1. Open the **Phone Assets** panel from the right sidebar chooser or **Tools → Phone Assets**
2. Edit the **App** tab for title, subtitle, wallpaper, home mode, status bar text, bubble/theme colors, and skin/chrome asset paths
3. Add contacts in **Contacts**
4. Create threads in **Threads**
5. Add launchable home-grid entries in **Apps**
6. Add or edit typed messages in **Messages**
7. Define voice/video call surfaces in **Calls**
8. Use the preview toolbar to switch between home, chat, and call views
9. Click **Save** to write the project phone config

## Message Types

The **Messages** tab can author these payload types:

- `TEXT`
- `IMAGE`
- `AUDIO`
- `MENU`
- `DATE`
- `LABEL`

## Asset Import

The panel supports both browsing and importing image assets:

- Wallpaper imports default into `assets/ui/phone/`
- Skin/chrome imports default into `assets/ui/phone/skins/`
- App icons import into `assets/ui/phone/apps/`
- Contact avatars import into `assets/phone/contacts/`
- Thread icons import into `assets/phone/chats/`
- Message payload artwork imports into `assets/phone/messages/`

Dropped or browsed files can also stay as project-relative references if you do not want the tool to copy them.

## Preview

The preview uses the shared JavaFX `PhoneRenderer`, not a separate editor-only mockup. You can switch between:

- **Preview Home** for the thread list or app grid, depending on `home.mode`
- **Preview Chat** for the currently selected thread
- **Preview Call** for the currently selected call surface

This makes it the correct place for phone-specific content and asset authoring rather than the layout editors, which are focused on general menu/dialogue geometry and styles.

## Related Docs

- [Sidebar Utilities Overview](../overview/sidebar-utilities.md)
- [VNS Interop](../../../scripting/vns/integration/vns-interop.md)
