# CLAUDE.md — AutoReplant Plugin

This document is a guide for AI assistants working on this codebase. Read it before making any changes.

---

## Project at a Glance

| Item | Value |
|------|-------|
| Purpose | Paper/Purpur 1.21.x plugin — auto-replants mature crops on harvest |
| Language | Java 21 |
| Build | Maven |
| Package root | `dev.autoreplant` |
| Supported server | Paper / Purpur 1.21.x (API 1.21.4-R0.1-SNAPSHOT) |
| Localization | Traditional Chinese (messages, in-code comments) |

---

## Directory Structure

```
AutoReplant/
├── pom.xml                                      # Maven build (Java 21, Paper API dep)
├── CLAUDE.md                                    # This file
├── README.md                                    # User-facing docs (Traditional Chinese)
└── src/main/
    ├── java/dev/autoreplant/
    │   ├── AutoReplantPlugin.java               # Main class — lifecycle, state, messaging
    │   ├── AutoReplantListener.java             # BlockBreakEvent — core replant logic
    │   └── AutoReplantCommand.java              # /autoreplant on|off (/arp)
    └── resources/
        ├── plugin.yml                           # Bukkit metadata, commands, permissions
        └── config.yml                           # Default config (loaded by saveDefaultConfig)
```

Runtime data written to `plugins/AutoReplant/`:
```
plugins/AutoReplant/
├── config.yml      # Live server config (copied from resources on first run)
└── players.yml     # Persisted per-player overrides (generated at runtime)
```

---

## How to Build

```bash
mvn package
```

Output: `target/AutoReplant-1.0.0.jar`
Drop into the server's `plugins/` folder and (re)start or `/reload`.

Maven resource filtering is enabled — `${project.version}` inside `plugin.yml` is resolved at build time.

---

## Architecture

### Class Responsibilities

#### `AutoReplantPlugin` (main class)
- Owns the **single source of truth** for player states (`Map<UUID, Boolean> playerStates`).
- Reads `default-enabled` from `config.yml` on startup.
- Stores only *overrides* (players whose preference differs from the default) — keeps `players.yml` small.
- Serialises/deserialises `playerStates` to/from `players.yml` using `YamlConfiguration`.
- Provides `getMessage(key, label)` which prepends the prefix, handles `<command>` substitution, and returns an Adventure `Component`.
- **Dual-syntax messaging**: `translateLegacy(String)` first converts `&` codes and `&#RRGGBB` hex to MiniMessage tags, then `MiniMessage.miniMessage().deserialize(...)` parses the result. Both syntaxes can be mixed freely in the same config string.
- `<command>` placeholder is resolved via `Placeholder.component(...)` (Adventure TagResolver) so it is always treated as plain text and never re-parsed by MiniMessage.

#### `AutoReplantListener` (event listener)
- Holds a `pendingReplants` map (`Map<Location, Material>`) and a plugin reference.
- Static `Map<Material, Material> CROP_TO_SEED` defines all supported crops and their seed items.
- Uses a **two-phase event architecture**:

**Phase 1 — `onBlockBreak` (priority `HIGHEST`, ignoreCancelled)**
  - Runs after all other plugins including protection plugins, so only unconditionally-broken blocks reach this handler.
  - Guard chain (all must pass):
    1. Player not in Creative mode
    2. Block material is a key in `CROP_TO_SEED`
    3. `block.getBlockData()` is `Ageable` and `age == maxAge` (fully grown)
    4. `plugin.isAutoReplantEnabled(player)` returns true
  - On pass: writes `pendingReplants.put(location.clone(), blockType)`.
  - On fail (player has auto-replant OFF): calls `pendingReplants.remove(location)` to evict any stale entry left by a previously-cancelled break at that position.

**Phase 2 — `onBlockDropItem` (priority `NORMAL`, ignoreCancelled)**
  - Fires only after the block has **actually broken** — guaranteed no cancellation race.
  - Drops in `event.getItems()` are the server's real computed values (includes Fortune, other-plugin modifications). No simulation needed.
  - Reads and removes the entry from `pendingReplants`; returns early if nothing was pending.
  - If `check-seeds` is enabled: calls `consumeOneSeed(items, seedMaterial)` — removes one seed `Item` from the list (or reduces its stack amount). Returns early if no seed found.
  - If `check-seeds` is disabled: skips seed check; all drops spawn normally.
  - Schedules a 1-tick delayed task to set the block back to the crop type (at default age 0).
  - Safety check in the task: position must be `AIR` and block below must be `FARMLAND`.

#### `AutoReplantCommand` (command + tab completer)
- Implements both `CommandExecutor` and `TabCompleter`.
- Guards: player-only, `autoreplant.use` permission, exactly one argument.
- Uses the command label (`label` param) for usage messages so `/arp` shows the correct label.
- Tab-completes `on`/`off` with prefix filtering.

---

## Supported Crops

| Block `Material` | Seed `Material` consumed | Max age |
|-----------------|--------------------------|---------|
| `WHEAT`         | `WHEAT_SEEDS`            | 7       |
| `CARROTS`       | `CARROT`                 | 7       |
| `POTATOES`      | `POTATO`                 | 7       |
| `BEETROOTS`     | `BEETROOT_SEEDS`         | 3       |

> Beetroots have a chance of dropping 0 seeds. In that case replanting is silently skipped (player gets all remaining drops normally — only `setDropItems(false)` is called after seed confirmation).

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| `EventPriority.HIGH` | Runs after most other plugins (default = NORMAL) but before HIGHEST/MONITOR, reducing conflicts. |
| Simulate drops with `getDrops(tool, player)` | Respects Fortune III on the tool; necessary for fair drop math. |
| 1-tick delay for replant | The block has not yet been removed when `BlockBreakEvent` fires; setting it immediately would be a no-op or conflict with Bukkit internals. |
| Only store overrides in `players.yml` | Minimises file I/O and keeps the YAML file readable; if a player flips back to default their entry is removed. |
| No replant message | Per-action chat spam on large farms is annoying; the toggle command already confirms the state. |
| Creative mode skip | Creative breaks don't drop items so seed consumption logic would silently fail. |
| `translateLegacy()` pre-processes `&` codes → MiniMessage tags | Enables `MiniMessage` as the single deserializer while preserving backward compat with `&` codes. The two syntaxes never conflict because `&x` and `<tag>` use distinct delimiters. |
| `Placeholder.component("command", ...)` instead of `.replace()` | Prevents `<command>` from being parsed as a MiniMessage tag and keeps the label as plain text. Safe against user-controlled label injection. |
| Two-phase event architecture (`BlockBreakEvent HIGHEST` + `BlockDropItemEvent`) | Eliminates the cancellation race: `BlockDropItemEvent` only fires when the break is confirmed. No need for `event.setDropItems(false)` or manual item spawning. Drops are real (not simulated). |
| `BlockBreakEvent` cleans up stale `pendingReplants` when player is opted-out | Prevents a race where Player A's cancelled break leaves an entry that Player B (opted-out) would accidentally trigger. |
| `check-seeds` config option | Server-admin toggle: `true` consumes one seed from drops (balanced), `false` replants for free (quality-of-life). Defaults to `true`. |

---

## Adding a New Crop

1. Open `AutoReplantListener.java`.
2. Add an entry to `CROP_TO_SEED`:
   ```java
   Material.NETHER_WART, Material.NETHER_WART  // example
   ```
3. Verify the crop block implements `Ageable` in Bukkit (`block.getBlockData() instanceof Ageable`).
4. Confirm what material is consumed as the seed (check vanilla loot tables).
5. No other files need changing.

---

## Conventions to Follow

### Java Style
- **Java 21 features are welcome**: switch expressions, pattern matching (`instanceof X x`), records if needed.
- Early-return guard clauses — avoid deeply nested if blocks.
- `final` on local variables that are not reassigned.
- `Objects.requireNonNull()` when retrieving registered commands (nullable in Bukkit API).

### Bukkit / Paper API
- Always use the `ignoreCancelled = true` annotation to avoid processing already-cancelled events.
- Use `block.getDrops(ItemStack tool, Entity entity)` — not the bare `getDrops()` — for proper enchantment simulation.
- Use `world.dropItemNaturally(location, item)` rather than `world.dropItem(...)` for randomised drop spread.
- All player-facing text must go through `plugin.getMessage(key, label)` — never call `MiniMessage` or `LegacyComponentSerializer` directly outside `AutoReplantPlugin`.
- Do **not** perform block mutations on the event thread if the block hasn't been broken yet — use a 1-tick `runTask` delay.

### Configuration
- All user-visible text goes in `config.yml`; no hard-coded strings in Java.
- Use `saveDefaultConfig()` (not `saveConfig()`) on first run so the user's existing config is never overwritten.
- `plugin.yml` version uses Maven filtering (`${project.version}`) — do not hardcode it.

### Persistence
- Player data lives in `players.yml` (generated at runtime, not bundled in the JAR).
- `savePlayerData()` **replaces** the entire `players` section each time; do not append.
- Load on `onEnable`, save on `onDisable` — do not save on every command to avoid I/O churn.

### Localization & Messaging
- Messages and in-code comments are in **Traditional Chinese**.
- New message keys go under `messages.*` in `config.yml`.
- Config values may use **MiniMessage tags** (`<green>`, `<bold>`, `<#ff0000>`, gradients, etc.) and/or **legacy `&` codes** (`&a`, `&l`, `&#00ff00`). Both are supported simultaneously via `translateLegacy()`.
- `<command>` is the only substitution placeholder; it must be passed as the `label` argument to `getMessage()` — do **not** use string concatenation or `.replace()` for it.
- To add a new placeholder: add a `Placeholder.component(...)` entry to the `MM.deserialize(...)` call in `getMessage()` and document it in the config comments.

---

## Common Pitfalls

| Pitfall | Avoidance |
|---------|-----------|
| Calling `block.setType()` in the same tick as `BlockBreakEvent` | Always use `runTask` (1-tick delay). |
| Forgetting `event.setDropItems(false)` before dropping items manually | This causes double drops; the guard is in the listener after `consumeOneSeed` returns `true`. |
| Modifying `drops` collection while iterating | `consumeOneSeed` only modifies the `amount` field of an existing `ItemStack` — it does not remove elements from the collection mid-iteration. |
| `block.getDrops()` ignoring Fortune | Pass both `tool` and `player` to `block.getDrops(ItemStack, Entity)`. |
| Saving all players regardless of default | Only store overrides; check `if (enabled == defaultEnabled) playerStates.remove(uuid)`. |
| Using `.replace("<command>", label)` on raw config strings | String replacement runs before MiniMessage parsing; `<command>` would be parsed as an unknown tag. Always use `Placeholder.component(...)` inside `MM.deserialize()`. |
| Calling `MiniMessage.deserialize()` directly on a config string without `translateLegacy()` | Legacy `&` codes in the string would appear as literal ampersand characters. Always go through `getMessage()`. |
| Modifying drops in `BlockBreakEvent` | If a HIGHEST-priority plugin later cancels the break, items were already dropped but the block is still there (free items bug). Always use `BlockDropItemEvent` to modify drops. |
| Iterating `List<Item>` with a for-each and calling `list.remove(item)` inside | Causes `ConcurrentModificationException`. Use an index-based `for (int i = 0; ...)` loop as done in `consumeOneSeed`. |
| Forgetting to clean stale `pendingReplants` entries | If a player with auto-replant OFF breaks a crop that has a stale entry (from a previously-cancelled break by another player with it ON), the stale entry is removed in `onBlockBreak` before it can trigger falsely. |

---

## Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `autoreplant.use` | `true` (all players) | Toggle auto-replant on/off |

---

## Commands

| Command | Alias | Arguments | Permission |
|---------|-------|-----------|------------|
| `/autoreplant` | `/arp` | `on` \| `off` | `autoreplant.use` |
