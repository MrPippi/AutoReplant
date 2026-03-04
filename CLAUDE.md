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
| Soft-depend | PlaceholderAPI, AutoPickup |
| Localization | Traditional Chinese (messages, in-code comments) |

---

## Directory Structure

```
AutoReplant/
├── pom.xml                                      # Maven build (Java 21, Paper API + PAPI dep)
├── CLAUDE.md                                    # This file
├── README.md                                    # User-facing docs (Traditional Chinese)
└── src/main/
    ├── java/dev/autoreplant/
    │   ├── AutoReplantPlugin.java               # Main class — lifecycle, state, messaging
    │   ├── AutoReplantListener.java             # BlockBreakEvent / BlockDropItemEvent / BlockFertilizeEvent
    │   ├── AutoReplantCommand.java              # /autoreplant [on|off|reload] (/arp)
    │   ├── AutoReplantExpansion.java            # PlaceholderAPI expansion (%autoreplant_status%)
    │   └── AutoPickupCompat.java               # Optional AutoPickup compatibility (reflection-based)
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
- Reads `default-enabled`, `check-seeds`, and `bone-meal-auto-replant` from `config.yml` on startup.
- Stores only *overrides* (players whose preference differs from the default) — keeps `players.yml` small.
- Serialises/deserialises `playerStates` to/from `players.yml` using `YamlConfiguration`.
- Provides `getMessage(key, label)` which prepends the prefix, handles `<command>` substitution, and returns an Adventure `Component`. Also provides `getMessage(key)` convenience overload (defaults label to `"autoreplant"`).
- **Dual-syntax messaging**: `translateLegacy(String)` first converts `&` codes and `&#RRGGBB` hex to MiniMessage tags, then `MiniMessage.miniMessage().deserialize(...)` parses the result. Both syntaxes can be mixed freely in the same config string.
- `<command>` placeholder is resolved via `Placeholder.component(...)` (Adventure TagResolver) so it is always treated as plain text and never re-parsed by MiniMessage.
- Creates `AutoPickupCompat` on startup via `AutoPickupCompat.create(this)` — returns `null` if AutoPickup is absent; stored as `autoPickupCompat`.
- Registers `AutoReplantExpansion` with PlaceholderAPI if that plugin is present at startup.
- Public accessors: `isAutoReplantEnabled(Player)`, `isAutoReplantEnabled(OfflinePlayer)`, `isAutoReplantEnabled(UUID)`, `isCheckSeedsEnabled()`, `isBoneMealAutoReplantEnabled()`, `getAutoPickupCompat()`.

#### `AutoReplantListener` (event listener)
- Holds a `pendingReplants` map (`Map<String, Material>`) keyed by **world UUID + integer block coordinates** (via `blockKey(Block)`).
- Static `Map<Material, Material> CROP_TO_SEED` defines all supported crops and their seed items.
- Uses a **two-phase event architecture** for normal harvesting, plus a separate handler for bone-meal.

**Phase 1 — `onBlockBreak` (priority `HIGHEST`, ignoreCancelled)**
  - Runs after all other plugins including protection plugins, so only unconditionally-broken blocks reach this handler.
  - Guard chain:
    1. Player not in Creative mode.
    2. Block material is a key in `CROP_TO_SEED`.
    3. `block.getBlockData()` is `Ageable`.
    4. If player has auto-replant ON and crop is **immature** (`age < maxAge`): `event.setCancelled(true)` — prevents accidentally breaking young crops when holding left-click. Returns immediately.
    5. If crop is not fully grown (and player is opted-out or immature check passed): return without queuing.
  - On pass (fully grown + player opted-in): writes `pendingReplants.put(blockKey, blockType)`.
  - On fail (player has auto-replant OFF, crop is mature): calls `pendingReplants.remove(key)` to evict any stale entry.

**Phase 2 — `onBlockDropItem` (priority `NORMAL`, ignoreCancelled)**
  - Fires only after the block has **actually broken** — guaranteed no cancellation race.
  - Reads and removes the entry from `pendingReplants`; returns early if nothing was pending.
  - If `check-seeds` is enabled:
    1. Calls `consumeOneSeedFromDrops(event.getItems(), seedMaterial)` — iterates the `List<Item>` drop entities and removes/reduces one matching item.
    2. If drops had no seed: calls `consumeSeedFromInventory(player, seedMaterial)` — material-only match, ignores ItemMeta.
    3. If neither had a seed: returns early (all drops spawn normally).
  - If `check-seeds` is disabled: skips seed check; all drops spawn normally.
  - Schedules a 1-tick delayed task: verifies `AIR` at position + `FARMLAND` below, sets block back to crop type (age 0), spawns `HAPPY_VILLAGER` particles via `spawnReplantParticle`.

**Bone-meal handler — `onBlockFertilize` (priority `NORMAL`, ignoreCancelled)**
  - Only active when `bone-meal-auto-replant` is `true`; skips if player is null or in Creative mode.
  - For each `BlockState` in `event.getBlocks()` that is a fully-grown supported crop: schedules a 1-tick delayed task calling `harvestAndReplantBoneMeal(blockLoc, blockType, player)`.
  - `harvestAndReplantBoneMeal`:
    - Validates block is still the right type at max age over FARMLAND.
    - Calls `block.getDrops(hand, player)` for Fortune-respecting drops.
    - Uses `computeDropsAfterSeedConsume(rawDrops, seedMaterial, checkSeeds)` → `DropResult` (final drop list + whether seed was consumed from drops).
    - Delivers drops via `AutoPickupCompat.giveDropsToPlayer` if AutoPickup is available and enabled for the player; otherwise uses `world.dropItemNaturally`.
    - If player has auto-replant OFF: sets block to AIR and returns (no replant).
    - If `check-seeds: true` and seed not yet consumed from drops: tries `consumeSeedFromInventory`; if fails, sets block to AIR (drops already spawned) and returns.
    - Sets block to crop type (age 0), spawns `HAPPY_VILLAGER` particles.

#### `AutoReplantCommand` (command + tab completer)
- Implements both `CommandExecutor` and `TabCompleter`.
- Guards: player-only, `autoreplant.use` permission, at most one argument.
- **No-argument invocation** (`/arp` with no args): toggles the player's current state.
- Uses the command label (`label` param) for usage messages so `/arp` shows the correct label.
- Tab-completes `on`/`off`/`reload` with prefix filtering.

#### `AutoReplantExpansion` (PlaceholderAPI expansion)
- Extends `PlaceholderExpansion`; registered only when PlaceholderAPI is present at startup.
- Identifier: `autoreplant`; `persist()` returns `true` (survives `/papi reload`).
- `onRequest(player, "status")` → `"ON"` or `"OFF"` using `isAutoReplantEnabled(OfflinePlayer)`.

#### `AutoPickupCompat` (optional compatibility layer)
- Instantiated via static `AutoPickupCompat.create(plugin)` — returns `null` if AutoPickup is absent or its API cannot be resolved.
- Uses **reflection** to call `AutoPickup.getStateManager().isEnabled(UUID)` and `AutoPickup.getFilterManager().shouldPickup(UUID, Material)` — no compile-time dependency on AutoPickup.
- `giveDropsToPlayer(player, stacks, dropLocation)`: for each stack, if AutoPickup is enabled for the player and the material passes its filter, adds to inventory (remainder drops naturally); otherwise drops naturally.

---

## Supported Crops

| Block `Material` | Seed `Material` consumed | Max age | Required base block |
|-----------------|--------------------------|---------|---------------------|
| `WHEAT`         | `WHEAT_SEEDS`            | 7       | `FARMLAND`          |
| `CARROTS`       | `CARROT`                 | 7       | `FARMLAND`          |
| `POTATOES`      | `POTATO`                 | 7       | `FARMLAND`          |
| `BEETROOTS`     | `BEETROOT_SEEDS`         | 3       | `FARMLAND`          |
| `NETHER_WART`   | `NETHER_WART`            | 3       | `SOUL_SAND`         |

> When `check-seeds: true`, replanting requires a seed: first checked in the event's drop list, then in the player's inventory. If neither has the seed, replanting is silently skipped and all drops spawn normally.
>
> `NETHER_WART` does not support bone meal in vanilla Minecraft, so it is never triggered by `BlockFertilizeEvent`.

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| `BlockBreakEvent` at `EventPriority.HIGHEST` | Runs after all other plugins (including protection plugins); only unconditionally-broken blocks reach this handler. |
| Cancel immature crop break when auto-replant is ON | Prevents accidentally destroying young crops when holding left-click on a farm. Only applies to players who have opted in — others can break immature crops freely. |
| Two-phase event architecture (`BlockBreakEvent HIGHEST` + `BlockDropItemEvent NORMAL`) | Eliminates the cancellation race: `BlockDropItemEvent` only fires when the break is confirmed. No need for `event.setDropItems(false)` or manual item spawning. Drops are real (not simulated). |
| `BlockBreakEvent` cleans up stale `pendingReplants` when player is opted-out | Prevents a race where Player A's cancelled break leaves an entry that Player B (opted-out) would accidentally trigger. |
| `check-seeds: true` consumes from drops before inventory | The seed is already in the drop list; consuming it there is more natural and avoids requiring an extra seed in the inventory slot. Falls back to inventory if drops contain no seed. |
| `check-seeds` config option | Server-admin toggle: `true` requires a seed (balanced), `false` replants for free (quality-of-life). Defaults to `true`. |
| 1-tick delay for replant | The block has not yet been removed when events fire; setting it immediately would conflict with Bukkit internals. Also needed for bone-meal to let the fertilize event complete before modifying the block. |
| Only store overrides in `players.yml` | Minimises file I/O and keeps the YAML file readable; if a player flips back to default their entry is removed. |
| No replant chat message | Per-action chat spam on large farms is annoying; the toggle command already confirms the state. `HAPPY_VILLAGER` particles provide subtle in-world feedback instead. |
| `bone-meal-auto-replant` via `BlockFertilizeEvent` | Decouples bone-meal harvesting from the two-phase break flow. Uses `block.getDrops(hand, player)` for Fortune simulation since `BlockDropItemEvent` does not fire for fertilization. |
| `AutoPickupCompat` via reflection | Allows AutoPickup integration without requiring it as a compile-time dependency; degrades gracefully to `null` when absent. |
| `AutoReplantExpansion` soft-registered | PlaceholderAPI expansion is only registered at runtime when PAPI is present; no startup error or dependency when absent. |
| `blockKey(Block)` = `"worldUID x y z"` | Reliable cross-event key using integer coordinates — avoids `Location.equals` issues and correctly handles sequential breaks of adjacent blocks. |
| Creative mode skip | Creative breaks don't drop items so seed consumption logic would silently fail. |
| `translateLegacy()` pre-processes `&` codes → MiniMessage tags | Enables `MiniMessage` as the single deserializer while preserving backward compat with `&` codes. The two syntaxes never conflict because `&x` and `<tag>` use distinct delimiters. |
| `Placeholder.component("command", ...)` instead of `.replace()` | Prevents `<command>` from being parsed as a MiniMessage tag and keeps the label as plain text. Safe against user-controlled label injection. |

---

## Adding a New Crop

1. Open `AutoReplantListener.java`.
2. Add an entry to `CROP_TO_SEED`:
   ```java
   Material.NEW_CROP, Material.NEW_CROP_SEED  // example
   ```
3. If the crop requires a base block other than `FARMLAND` (e.g. `SOUL_SAND`), add it to `NEEDS_SOUL_SAND`.
4. Verify the crop block implements `Ageable` in Bukkit (`block.getBlockData() instanceof Ageable`).
5. Confirm what material is consumed as the seed (check vanilla loot tables).
6. No other files need changing.

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
| `block.getDrops()` ignoring Fortune | Pass both `tool` and `player` to `block.getDrops(ItemStack, Entity)`. |
| Saving all players regardless of default | Only store overrides; check `if (enabled == defaultEnabled) playerStates.remove(uuid)`. |
| Using `.replace("<command>", label)` on raw config strings | String replacement runs before MiniMessage parsing; `<command>` would be parsed as an unknown tag. Always use `Placeholder.component(...)` inside `MM.deserialize()`. |
| Calling `MiniMessage.deserialize()` directly on a config string without `translateLegacy()` | Legacy `&` codes in the string would appear as literal ampersand characters. Always go through `getMessage()`. |
| Modifying drops in `BlockBreakEvent` | If a HIGHEST-priority plugin later cancels the break, items were already dropped but the block is still there (free items bug). Always use `BlockDropItemEvent` to modify drops. |
| Using `Inventory.removeItem(new ItemStack(...))` for seed check | `removeItem` uses `isSimilar` which compares ItemMeta — a renamed seed would not match. `consumeSeedFromInventory` iterates slots and checks only `Material` type, so custom-named seeds are consumed correctly. |
| Forgetting to clean stale `pendingReplants` entries | If a player with auto-replant OFF breaks a crop that has a stale entry (from a previously-cancelled break by another player with it ON), the stale entry is removed in `onBlockBreak` before it can trigger falsely. |
| Using `Location` objects as `pendingReplants` map keys | `Location.equals` can be unreliable across events. Always use `blockKey(Block)` (world UUID + integer coords) as the key. |
| Calling `AutoPickupCompat` methods without null-check | `getAutoPickupCompat()` returns `null` when AutoPickup is absent. Always check `compat != null && compat.isAvailable()` before use. |
| Modifying drops inside `BlockFertilizeEvent` | No `BlockDropItemEvent` fires for bone-meal. Use `block.getDrops(hand, player)` and spawn drops manually (or via `AutoPickupCompat.giveDropsToPlayer`). |

---

## Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `autoreplant.use` | `true` (all players) | Toggle auto-replant on/off (including no-arg toggle) |
| `autoreplant.reload` | `op` | Reload config.yml at runtime |

---

## Commands

| Command | Alias | Arguments | Permission | Description |
|---------|-------|-----------|------------|-------------|
| `/autoreplant` | `/arp` | *(none)* | `autoreplant.use` | Toggle current state |
| `/autoreplant` | `/arp` | `on` | `autoreplant.use` | Enable auto-replant |
| `/autoreplant` | `/arp` | `off` | `autoreplant.use` | Disable auto-replant |
| `/autoreplant` | `/arp` | `reload` | `autoreplant.reload` | Reload config.yml |
