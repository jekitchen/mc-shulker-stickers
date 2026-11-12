# Shulker Stickers

Organize your shulker boxes at a glance. Sneak and right-click a placed shulker box with an empty hand to open the **Sticker editor**, where you can:

- **Recolor** the box to any of the 16 dye colors, or back to undyed. No dye needed, and everything inside stays put.
- **Stick an icon** on it from the item in your off-hand. You keep the item, and no item frame is needed.
- **Rename** it for free, with no anvil, name tag or XP. The name is shown on the box under the icon.

Changes are staged in the editor, and **Save** applies them all at once; **Cancel** or **Esc** discards them.

Everything runs on the server. Players need **no client mod** to see icons and names or to use the editor. Removing the mod or plugin leaves your boxes, their contents, names and colors intact, and leaves no stray entities in the world.

> **Status:** in development, not released yet. See [`docs/PRD.md`](docs/PRD.md) for the full product spec.

## Platforms

| Platform | Build | Requires |
|---|---|---|
| Fabric (server or singleplayer) | `shulker-stickers-fabric` mod | Fabric API (Polymer is bundled) |
| Paper and its forks | `shulker-stickers-paper` plugin | Nothing else |
| Folia / Canvas | the same Paper plugin | Nothing else |

Minecraft **26.3** or newer, Java 25.

Sticker data uses the same format on every platform, so stickered items keep their stickers when moved between Fabric and Paper servers.

## Using it

1. Hold the item you want as an icon in your **off-hand**, and keep your main hand empty.
2. **Sneak and right-click** a placed shulker box.
3. Pick a color, click **Use off-hand item**, and/or **Rename…**.
4. Click **Save**.

The icon goes on the face you clicked. Break, carry, hopper or dispense the box anywhere: when it's placed again, it looks the same.

The first time you place a shulker box, a short tip explains this. `/shulkerstickers help` shows it again.

## Commands

| Command | Who | What it does |
|---|---|---|
| `/shulkerstickers help` | Everyone | Shows how to use the editor |
| `/shulkerstickers toggle` | Everyone | Turns the sneak-click gesture off or on for you |
| `/shulkerstickers inspect` | Admins | Shows the sticker data of the box you're looking at |
| `/shulkerstickers clear [icon\|name\|all]` | Admins | Clears the box you're looking at |
| `/shulkerstickers reload` | Admins | Reloads the config |

## Permissions

| Node | Default |
|---|---|
| `shulker_stickers.color`, `shulker_stickers.icon`, `shulker_stickers.name` | Everyone |
| `shulker_stickers.name.display` | Everyone (names set by players without it aren't shown on boxes) |
| `shulker_stickers.admin` | Operators (level 2) |

Paper uses the server's permission system (LuckPerms and others work automatically). Fabric uses the Fabric Permissions API if it's installed, otherwise operator levels.

Editing a box needs **build** permission where the box is, not just permission to open it, so claim and protection plugins keep working. On Fabric that's spawn protection and any claim mod using the Common Protection API. On Paper, GriefPrevention, WorldGuard, Lands and Towny are asked directly when installed, and plugins that block right-clicking the box work too. For other claim plugins, see `protection.simulate_place_event` below.

## Configuration

Fabric: `config/shulker_stickers.json`. Paper: `plugins/ShulkerStickers/config.json`. The file is created with defaults on first start; `/shulkerstickers reload` applies changes.

```json
{
  "color": { "enabled": true, "cost": { "type": "none" } },
  "icon": {
    "enabled": true,
    "cost": { "type": "none" },
    "blocklist": [],
    "max_data_bytes": 2048,
    "view_range": 32,
    "full_bright": false
  },
  "name": {
    "edit_enabled": true,
    "display_enabled": true,
    "cost": { "type": "none" },
    "view_range": 10,
    "max_display_chars": 24,
    "blocked_words": []
  },
  "display": { "hide_while_open": true },
  "help": { "first_place_message": true },
  "allow_adventure_mode": false,
  "protection": { "simulate_place_event": false }
}
```

- **Costs** can be `{"type": "none"}`, `{"type": "xp_levels", "amount": 2}` or `{"type": "item", "item": "minecraft:paper", "amount": 1}`. They're charged on Save, only for what changed.
- **`icon.blocklist`** lists item IDs that can't be used as icons. The item tag `#shulker_stickers:icon_blocklist` works too.
- **`name.blocked_words`** entries match whole words, ignoring case. Wrap an entry in slashes (`"/b[a4]d/"`) to use a regular expression.
- **`icon.enabled`** set to `false` also hides icons already on boxes. The data stays, so turning it back on restores them.
- **`protection.simulate_place_event`** (Paper only, off by default) asks claim plugins we don't support directly by firing a test block-place event. Leave it off if you run a logging plugin such as CoreProtect: it can log the editor as having placed the box, and rolling that player back would remove the box.

## Building

```sh
./gradlew build
```

This builds both jars and runs every test: `core` unit tests, Fabric GameTests and Paper tests. The jars end up in `fabric/build/libs/` and `paper/build/libs/`.

To try it on a local test server:

| Task | Server |
|---|---|
| `./gradlew :fabric:runServer` | Fabric |
| `./gradlew :paper:runServer` | Paper |
| `./gradlew :paper:runFolia` | Folia |
| `./gradlew :paper:runCanvas` | Canvas |
| `./gradlew :paper:runClaims` | Paper with WorldEdit, WorldGuard and GriefPrevention |

Add `-Pselftest` to any Paper task to run the built-in real-server checks and stop, and `-Pserverport=N` to use another port.

Two scripts run the real-server checks the way CI does, and fail if anything fails:

| Script | What it checks |
|---|---|
| `scripts/selftest.sh runServer` (or `runFolia`, `runCanvas`, `runClaims`) | The Paper plugin's built-in checks on that server |
| `scripts/paper-uninstall.sh` | The self-test, then the same world on Paper without the plugin (AC-6) |
| `scripts/fabric-smoke.sh` | The Fabric release jar on a real dedicated server with only Fabric API installed: a restart with a saved decorated box, uninstall (AC-6), and next to Ultimate Shulker Trims |

Releases are described in [`docs/release/README.md`](docs/release/README.md), and changes in [`CHANGELOG.md`](CHANGELOG.md).

The code lives in three modules: `core` holds every platform-independent rule (data format, editor, config, translations), and `fabric` and `paper` adapt it to each server.

## License

MIT, see [`LICENSE`](LICENSE).

The Fabric build bundles [Polymer](https://github.com/Patbox/polymer) (LGPL-3.0), unmodified; its source is available at that link. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md); the license texts are in `licenses/` and inside the Fabric jar.
