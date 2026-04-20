# VNS By Example — Hello World

VNS (Visual Novel Script) is a domain-specific language for creating visual novels. It's designed to be simple and easy to learn, while still being powerful enough to create complex stories.

Below is the simplest possible VNS script: a narrator speaks, and the story ends.

**Difficulty:** Beginner
**Time:** 5 minutes
**Concepts:** `@scenario`, `@character`, dialogue lines, `@label`, `[end]`, running a script

---

## The Script

```vns
@scenario hello
@character narrator "Narrator"

@label start
narrator: Hello from VNS!
narrator: This is the simplest possible visual novel script.
narrator: Press the screen or hit Enter to advance.
[end]
```

## Run It

```bash
./gradlew :runtime:run --args='scripts/hello.vns'
```

You should see a dialogue box with the Narrator speaking three lines, then the scenario ends.

---

## Anatomy of a VNS Script

### `@scenario` — Script Declaration

Every VNS file must begin with a `@scenario` directive:

```vns
@scenario hello
```

- The name is used internally for identification, save data, and arc navigation
- It should be a short, unique identifier (lowercase, no spaces)
- One `@scenario` per file

### `@character` — Character Declaration

Before a character can speak, they must be declared:

```vns
@character narrator "Narrator"
```

| Part | Description |
|------|-------------|
| `narrator` | Internal ID used in dialogue lines |
| `"Narrator"` | Display name shown in the name plate |

You can declare multiple characters:

```vns
@character narrator "Narrator"
@character hero "Yuki"
@character friend "Sakura"
```

### `@label` — Story Bookmarks

Labels mark positions in the script that can be jumped to:

```vns
@label start
```

- Labels are referenced by `[jump]`, `[goto]`, `[if ... goto]`, and other flow commands
- The first label in a script is where playback begins by default
- Names should be lowercase with underscores: `chapter1_start`, `good_ending`, etc.

### Dialogue Lines

Dialogue is written as `characterId: text`:

```vns
narrator: Hello from VNS!
hero: I have something to say.
friend: Me too!
```

- The character ID must match a declared `@character`
- The text after the colon is displayed in the dialogue box
- Each line waits for the player to advance (click, tap, or Enter)
- Lines without a colon are treated as narrator/continuation text

### `[end]` — End the Story

```vns
[end]
```

Terminates the scenario. The runtime returns to the main menu or exits, depending on configuration.

---

## Directives vs Commands

VNS has two syntax forms:

| Form | Syntax | When It Runs |
|------|--------|--------------|
| **Directives** | `@keyword args` | At parse time — declares metadata |
| **Commands** | `[command args]` | At runtime — performs actions |

Examples:
- `@character hero "Yuki"` — **directive** (declares a character at parse time)
- `[show hero center]` — **command** (shows the character at runtime)
- `@label start` — **directive** (marks a position)
- `[jump end]` — **command** (navigates at runtime)

---

## Comments

Lines starting with `#` are comments (ignored by the parser):

```vns
# This is a comment
@scenario demo
@character narrator "Narrator"

# Introduction
@label start
narrator: Hello!   # inline comments also work
[end]
```

---

## Variations to Try

### Multi-Character Conversation

```vns
@scenario conversation
@character narrator ""
@character hero "Yuki"
@character friend "Sakura"
@character rival "Takeshi"

@label start
narrator: It was a quiet afternoon.
hero: Hey, has anyone seen my notebook?
friend: I think Takeshi had it earlier.
rival: What? I didn't take anything!
hero: Then where is it...?
friend: Let's check the classroom.
[end]
```

### Narrator with No Name

Use an empty display name for narrator text without a name plate:

```vns
@character narrator ""

@label start
narrator: The wind blew across the empty field.
narrator: Nothing stirred.
[end]
```

### Multiple Labels

```vns
@scenario demo
@character narrator "Narrator"

@label start
narrator: This is the beginning.
[jump middle]

@label middle
narrator: Now we're in the middle.
[jump ending]

@label ending
narrator: And this is the end.
[end]
```

---

## Key Takeaways

1. Every VNS file starts with `@scenario name`
2. Characters are declared with `@character id "Display Name"`
3. Dialogue uses `characterId: text` syntax
4. `@label name` marks jump targets
5. `[end]` terminates the scenario
6. Directives (`@`) run at parse time; commands (`[]`) run at runtime
7. Comments start with `#`

---

## Next

- [Characters and Backgrounds](02-characters-and-backgrounds.md) — showing characters and setting scenes
- [Back to Index](../vns-by-example.md)
