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
- Provides `getMessage(key, label)` which prepends the prefix, substitutes `<command>`, and returns an Adventure `Component`.
- Uses `LegacyComponentSerializer.legacyAmpersand()` to parse `&` colour codes from config strings.

#### `AutoReplantListener` (event listener)
- Stateless — holds only a reference to the plugin.
- Static `Map<Material, Material> CROP_TO_SEED` defines all supported crops and their seed items.
- `onBlockBreak` guard chain (all must pass):
  1. Player not in Creative mode
  2. Block material is a key in `CROP_TO_SEED`
  3. `block.getBlockData()` is `Ageable` and `age == maxAge` (fully grown)
  4. `plugin.isAutoReplantEnabled(player)` returns true
  5. `consumeOneSeed()` finds at least one seed in the simulated drops
- Calls `block.getDrops(tool, player)` (not the no-arg overload) to respect Fortune enchantment.
- Sets `event.setDropItems(false)` and drops items manually so the seed removal is reflected.
- Schedules a **1-tick delayed task** (`Bukkit.getScheduler().runTask(plugin, ...)`) for the replant so the block is already air when `setType` is called.
- Safety check inside the delayed task: position must be `AIR` and the block directly below must be `FARMLAND`.

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
- Prefer Adventure `Component` over legacy `String` messaging. Use `LEGACY.deserialize(...)` only when reading `&`-coded strings from config.
- Do **not** perform block mutations on the event thread if the block hasn't been broken yet — use a 1-tick `runTask` delay.

### Configuration
- All user-visible text goes in `config.yml`; no hard-coded strings in Java.
- Use `saveDefaultConfig()` (not `saveConfig()`) on first run so the user's existing config is never overwritten.
- `plugin.yml` version uses Maven filtering (`${project.version}`) — do not hardcode it.

### Persistence
- Player data lives in `players.yml` (generated at runtime, not bundled in the JAR).
- `savePlayerData()` **replaces** the entire `players` section each time; do not append.
- Load on `onEnable`, save on `onDisable` — do not save on every command to avoid I/O churn.

### Localization
- Messages and in-code comments are in **Traditional Chinese**.
- New message keys go under `messages.*` in `config.yml`.
- `<command>` is the only substitution placeholder in messages; replace with the actual label from the command handler.

---

## Common Pitfalls

| Pitfall | Avoidance |
|---------|-----------|
| Calling `block.setType()` in the same tick as `BlockBreakEvent` | Always use `runTask` (1-tick delay). |
| Forgetting `event.setDropItems(false)` before dropping items manually | This causes double drops; the guard is in the listener after `consumeOneSeed` returns `true`. |
| Modifying `drops` collection while iterating | `consumeOneSeed` only modifies the `amount` field of an existing `ItemStack` — it does not remove elements from the collection mid-iteration. |
| `block.getDrops()` ignoring Fortune | Pass both `tool` and `player` to `block.getDrops(ItemStack, Entity)`. |
| Saving all players regardless of default | Only store overrides; check `if (enabled == defaultEnabled) playerStates.remove(uuid)`. |

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
