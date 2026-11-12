# M0 spike and 0.1 build-out: handoff (11 Nov 2026)

**Result:** go on all three platforms. Fabric, Paper and Canvas (and Folia) run the same features from one shared `core`, and every automated check passes. All eleven owner decisions on the v0.4 proposals are built (see below). What's left needs a real game client.

Merged to `main` as PR #2 on 12 Nov 2026. **For the current 0.1 status, see [`docs/release/0.1-status.md`](../release/0.1-status.md)**: the test counts and coverage below are from 11 Nov and have since grown. The client checks at the end of this page are still the manual list for 0.1.

## Where things stand

| | Fabric | Paper / Folia / Canvas |
|---|---|---|
| Automated tests | 87 GameTests (incl. Fabric Permissions API and Common Protection API run in tests) | 107 unit tests (MockBukkit) + real-server self-test: 28 checks on Paper 249, Folia 6 and Canvas 1010; 30 on Paper with real WorldGuard and GriefPrevention |
| Shared `core` | 101 unit tests | (same) |
| Editor, recolor, icon, rename, Save/Cancel | Done | Done |
| Data survives break / pistons / explosions / dispensers | Done (pistons and explosions tested since 12 Nov) | Done (built-in datapack; pistons and explosions tested since 12 Nov) |
| Config, translations, permissions, costs, locking, hide-while-open, first-run tip, commands | Done | Done |
| Items move between Fabric and Paper | Identical bytes (checked by a parity review) | (same) |
| AC-8 (1,000 boxes < 0.5 ms/tick) | 0.04 ms | Under budget with no player near; ~0.7 ms with a player in view of all 1,000 at the old name range. Accepted for 0.1 with the name range now 10 (owner decision 6) |
| Claims | Spawn protection, `mayInteract`, Common Protection API | Cancelled right-click, plus GriefPrevention, WorldGuard, Lands and Towny asked directly; opt-in test event for others |
| World moved between platforms | Paper-saved boxes adopted on load | Fabric-saved boxes adopted on chunk load |

`./gradlew build` runs all of it except the Paper self-test (`./gradlew :paper:runServer -Pselftest`, `:paper:runFolia -Pselftest`, `:paper:runCanvas -Pselftest`, and `:paper:runClaims -Pselftest` for Paper with real WorldEdit, WorldGuard and GriefPrevention).

Each platform had an independent code review, then a cross-platform parity review, then a review of the fixes (which caught a free-edit hole in Paper payments, now fixed: payment is taken at Save and unapplied parts are refunded). Every critical and important finding was fixed with a test that failed first.

## Owner decisions (11 Nov 2026): all built

All eleven v0.4 proposals are decided (`docs/PRD.md` §12) and built:

1. **Data format** (`face`, JSON `icon_visual`, `name_hidden`): already in both builds.
2. **Locked boxes:** the key counts in either hand on both. Paper's API can't test an item against a lock, so Paper asks the server's own `LockCode#unlocksWith` by reflection (Paper 26.x runs on Mojang names). If that ever fails, locked boxes are refused and a warning is logged once. Checked at open and again at Save.
3. **Claims on Paper:** see `paper-0.1-notes.md` "Protection approach". Checked at open and at Save.
4. **Worlds moved between platforms:** Fabric adopts a Paper-saved box's root `PublicBukkitValues` when the block entity loads (`BlockEntityLoadMixin`). Paper moves a Fabric-saved box's `custom_data` keys into persistent data on chunk load, and strips our keys from `custom_data` (that copy goes stale on Paper). Paper reads and edits `custom_data` by reflection; on failure nothing changes. Fabric adopts Paper's values only when the root holds at least one of our keys, so another plugin's data alone never wipes a Fabric sticker. **Known edge cases** (from the review): a box whose sticker was fully removed on Paper by a build older than `78bfc80` still has the stale item copy in `custom_data`, so the old sticker can reappear on Paper's next load or after a move to Fabric. Only spike builds are affected, so no fix is planned.
5. **Drop copy:** only our keys, on both. Paper also warns at startup when a world datapack overrides shulker loot tables, and repairs player-break drops that lost their sticker.
6. **AC-8:** `name.view_range` default is now 10.
7. **`icon.enabled = false`** hides existing icons on both.
8. **Name without an icon**, 9. **blocked words**, 10. **failed Save**: already in both builds.
11. **Shared look:** `core` now holds `DisplayLayout`, `EditorLook` and `InspectReport`, starting from Fabric's values. Paper changed: icon scale 0.45 to 0.5, name sits lower, filler panes, unavailable buttons keep their item, off-hand placeholder is light-grey dye, `inspect` shows the name.

## Checks that need a vanilla 26.3 client

Start a server with `./gradlew :fabric:runServer`, `:paper:runServer` or `:paper:runCanvas` and join with an unmodded 26.3 client. Hold cobblestone in the off-hand.

**Shortcut: the showcase.** Copy `showcase/shulker_stickers_showcase` into the server's `world/datapacks/` folder (`fabric/run/world/datapacks` or `paper/run/world/datapacks`) and run `/reload`. Then stand on open, flat ground as an operator and run `/function shulker_stickers_showcase:build`. On Paper, also run `/shulkerstickers reload` so the plugin draws boxes made by commands. It builds four rows of decorated boxes south of you:

| Row | Contents | Checks |
|---|---|---|
| 1 | Every facing, with the icon on the lid | 4, 7 |
| 2 | Side faces, and the lid icon turned four ways | 4 |
| 3 | Icon looks: dyed armor, glint, a head, a banner; a name-only box, a long name, a hidden name | 5, 6 |
| 4 | A box locked with a stick (the key is in the chest next to it), and a plain box to decorate | 1–3, 10 |

**Already seen with a real client (Fabric, 12 Nov).** `./gradlew :fabric:runClientGameTest` runs `StoreScreenshots` in a real 26.3 client (singleplayer, so the integrated server runs the mod). It opened the editor with a real sneak + right-click and a diamond in the off-hand (check 1, without the ghost-block part). It also drew south-facing lid icons, up-facing lid icons with rotations 0 and 1, and a north side icon, all upright and not mirrored (part of check 4). Names were readable under the icons (check 6). See `docs/images/screenshots/`. Paper, and everything else below, still needs a person.

1. **Gesture:** sneak + right-click a placed box with an empty main hand opens "Edit: …". With a block in the off-hand, no block is placed and no ghost block shows.
2. **Menu safety:** try shift-click, number keys, F, double-click, drag, Q and clicking outside the window. Nothing leaves the editor and no ghost items appear.
3. **Rename screen:** type a name and click the result slot at 0 XP. It must work at repair cost 0, the paper must never reach the inventory, and Esc must return to a working editor.
4. **Icons:** on all six faces, the icon faces outward and isn't mirrored. On top and bottom, the image's top points the way you were facing. Both builds assume the display model's front faces +Z; if every icon looks backwards, that one assumption is wrong.
5. **Icon looks:** a dyed leather helmet, an enchanted sword (glint), a player head and a banner should each look like the item.
6. **Names:** readable under the icon, and centered on the face for a name-only box.
7. **Hide while open:** the displays disappear while the box is open and come back after it closes.
8. **Costs, permissions, commands, first-run tip, dispenser, walking out of reach:** see the lists in `docs/spike/fabric-0.1-notes.md` and `docs/spike/paper-0.1-notes.md`.
9. **Optional Fabric integrations with real mods:** LuckPerms and a Common Protection API claim mod (e.g. Flan). Both APIs are exercised in GameTests with fake providers, but not yet with the real mods.
10. **Lock key:** lock a box with `/data merge block <pos> {lock:{items:"minecraft:stick"}}` (or a named key). With the key in the off-hand and an empty main hand, the editor opens on both builds; without it, "locked". The icon picker then offers only the key.
11. **Claims on Paper** (`:paper:runClaims`): in a GriefPrevention claim, a container-trusted player is refused and a build-trusted player can edit. Same for a WorldGuard region with and without build. With `/ignoreclaims` or region bypass, editing works.
12. **World move:** decorate boxes on Fabric, copy the world folder into a Paper server, and check they still show their stickers; then the other way round. Also edit a box on Paper, move the world to Fabric, and check it shows the Paper edit. (Since 12 Nov, `scripts/world-move.sh` checks the data side of this automatically; what's left is seeing the icons. Paper → Fabric needs the extra file copy described in the README.)
13. **Shared look:** compare the editor and a decorated box side by side on Fabric and Paper; tune `DisplayLayout` and `EditorLook` in `core` if needed.
14. **Restart (both, 12 Nov fixes):** decorate a few boxes near spawn, restart the server, and check it starts and the icons and names show again. Also walk far away and back so their chunks unload and reload. Before the fixes, Fabric froze at "Preparing spawn area", and Paper didn't draw boxes in chunks that were loaded before the plugin (spawn chunks).
15. **Chunk edge (Paper, 12 Nov change):** put a box in the last block column of a chunk (F3 shows chunk borders) with its icon on the face toward the next chunk. The icon shows in front of the face, at the same brightness as a box in the middle of a chunk.

## Files

- Spec: `docs/PRD.md` (v0.5)
- Plan: `docs/superpowers/plans/2026-11-11-core-consolidation-and-0.1.md`
- Findings: `docs/spike/m0-fabric.md`, `docs/spike/m0-paper.md`, `docs/spike/fabric-0.1-notes.md`, `docs/spike/paper-0.1-notes.md`, `docs/spike/perf-ac8.md`
