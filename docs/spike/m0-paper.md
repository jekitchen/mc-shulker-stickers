# M0 spike: Paper, Folia and Canvas findings

Date: 11 Nov 2026 · Module: `paper/` · PRD: [`docs/PRD.md`](../PRD.md) §11 M0

**Go for Paper and Canvas.** The rendering approach (real display entities that are never saved) works on Paper, Folia and Canvas. So do the recolor swap, sticker data surviving a drop, and region-thread scheduling. Every automated check passes on all three servers. What's left needs a vanilla client in hand (see "Manual checks").

## What works

| Area | Status | Where |
|---|---|---|
| Gesture: sneak + right-click, empty main hand | Opens the editor, stops the box opening, also stops the off-hand placement | `GestureListener` |
| Sticker editor (3-row menu) | 17 colors, Use off-hand item, Remove icon, Rename…, Cancel, Save; changes are staged in core's `EditorSession`; Esc and Cancel discard; one editor per box | `EditorService`, `EditorLayout` |
| Menu safety (AC-12) | Every click in the editor is cancelled; only a plain left or right click on a button does anything; drags cancelled | `EditorLayout.isButtonPress` |
| Recolor (AC-1, AC-16) | Block-entity snapshot → change type → write back with physics off. Keeps items, name, lock, persistent data and facing. No observer pulse | `BoxRecolorer` |
| Sticker data | Keys `shulker_stickers:{v, icon_id, icon_visual, anchor, rot}` in the block's and item's persistent data (`PublicBukkitValues`) | `StickerStore`, `StickerKeys` |
| Data survives drops | **Built-in datapack** overrides the 17 shulker loot tables with an extra `copy_custom_data` step. Works on every loot-table drop path | `ShulkerStickersBootstrap`, `resources/stickers_datapack` |
| Placement | Item data copied to the block on `BlockPlaceEvent`; a `side` icon goes on the side facing the placer | `StickerTransferListener`, `Faces` |
| Displays | `ItemDisplay` + `TextDisplay`, `setPersistent(false)`, spawned just outside the face, name under the icon at 0.3 scale, 24-char cap. Re-made on chunk load, removed on unload, break, explosion and piston | `DisplayManager`, `DisplayListener` |
| Rename screen | `MenuType.ANVIL` (no anvil block), `AnvilView#getRenameText()`, result shown via `PrepareAnvilEvent`; Esc returns to the editor; the anvil is cleared on close so the paper never reaches the player | `EditorService` |
| Folia/Canvas threading | World and entity work only on the `RegionScheduler`; menus and messages on the player's `EntityScheduler`; the self-test uses the `GlobalRegionScheduler` only to start. No `BukkitScheduler` anywhere | all |

**Not done in the spike:** hiding displays while the box is open; icon visual properties (`icon_visual` is stored as `{}`); config, commands, permissions, build-permission checks and costs; translations (English literals); the first-placement help message; the name blocklist.

## Test results

- **Unit tests (MockBukkit 4.120.0):** 31 tests, 0 failures, 0 skipped. Run with `./gradlew :paper:test`.
  - `StickerStoreTest` 6, `EditorLayoutTest` 6, `FacesTest` 6, `BoxRecolorerTest` 2, `StickerTransferListenerTest` 2, `EditorFlowTest` 9.
  - I checked that the editor-flow tests can fail: removing the click cancel or the off-hand deny makes the matching tests go red.
- **Real-server self-test:** `./gradlew :paper:runServer|runFolia|runCanvas -Pselftest`. This accepts the EULA, runs the checks below, logs `SELFTEST PASS/FAIL` lines and shuts the server down.

| Server | Build | Result | Errors / thread-check failures |
|---|---|---|---|
| Paper | 26.3-248 (stable) | **11/11 pass** | 0 / 0 |
| Folia | 26.3-6 (alpha) | **11/11 pass** | 0 / 0 |
| Canvas | 26.3-1010 (stable) | **11/11 pass** | 0 / 0 |
| Paper, datapack off (control) | 26.3-248 | 8/11: the tag and both drop checks fail, as expected | 0 / 0 |

Self-test checks:
1. The datapack item tag `#shulker_stickers:icon_blocklist` is visible to plugins.
2. AC-1: a recolor through all 17 variants keeps 27 slots, the name, the lock, the icon and `facing`.
3. AC-1: no item entities are dropped.
4. AC-16: an observer watching the box doesn't pulse on recolor.
5. AC-16 control: the same observer *does* pulse on a normal block change, so the test can fail.
6. The sticker reaches `getDrops()` through the datapack.
7. Name and contents also reach the drop (vanilla loot).
8. A `breakNaturally()` drop carries the sticker.
9. Displays spawn from a region thread.
10. Displays are non-persistent.
11. Displays are removed with the box.

## Answers to the PRD §12 verify list (Paper + Canvas)

| Item | Answer | Evidence |
|---|---|---|
| `PlayerInteractEvent` can stop the off-hand placement | **Yes, by denying the separate OFF_HAND event.** Cancelling the main-hand event alone doesn't stop it: when the main hand does nothing, the client sends a second use packet for the off hand. We deny both while the gesture conditions hold | `EditorFlowTest.theOffHandPlacementThatFollowsTheGestureIsBlocked`; needs a client check for a flickering ghost block |
| Paper's anvil view API for the rename text | **Exists:** `MenuType.ANVIL.builder().title(..).checkReachable(false).build(player)`, `AnvilView#getRenameText()`, `setRepairCost`, `PrepareAnvilEvent#setResult` | `javap` of paper-api 248; compiles; **not run with a client** |
| Block-entity persistent data reaches the dropped item | **No, by default** (the control run loses it). **Yes, with our datapack:** `copy_custom_data` from `block_entity` path `PublicBukkitValues."shulker_stickers:…"`. This covers players, pistons, explosions, `breakNaturally` and `/setblock destroy`, because all of them use the loot table | Self-test checks 6 and 8; control run |
| Entities marked never-to-save on chunk unload | `setPersistent(false)` is confirmed and the entities are removed cleanly. We drop our references on `ChunkUnloadEvent` and re-spawn on `ChunkLoadEvent` (re-scheduled on the chunk's region). **A real unload/reload cycle wasn't exercised** (no player to move) | Self-test checks 9–11; code |
| Datapack item tags available to plugins | **Yes**, when the plugin ships the tag in its own datapack: `Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class)` | Check 1; null in the control run |
| MockBukkit for 26.3 | **Usable:** `mockbukkit-v26.3:4.120.0`. Gaps: block-state snapshots don't copy the block's data (the test sets it); `Inventory#getHolder(boolean)` throws "Not implemented"; no Folia region semantics; no `MenuType`/anvil; no real loot tables or observers. That's why the self-test exists | Tests |
| Canvas has a 26.3 build | **Yes:** build 1010, stable channel, loads the plugin with `folia-supported: true` | Canvas run |
| Region and entity scheduler APIs | As researched: `RegionScheduler.execute/run/runDelayed/runAtFixedRate(plugin, location or world+chunk, …)`; `entity.getScheduler().run(plugin, task, retired)` | `javap`; used throughout; no thread-check errors on Folia or Canvas |
| Display entities from a region thread | **Yes**, `world.spawn(loc, ItemDisplay.class, init)` inside a region task | Check 9 on Folia and Canvas |

## Manual checks still needed (vanilla 26.3 client)

1. **Gesture:** sneak + right-click with an empty main hand opens "Edit: …". The box doesn't open. With a block in the off-hand, no block is placed and no ghost block lingers.
2. **Menu safety:** try every click type (shift, number keys 1–9, F swap, double-click, drag, Q/Ctrl-Q, clicking outside, creative middle-click). Nothing should leave the editor and the player's inventory should be unchanged.
3. **Rename screen:**
   - The text field appears, pre-filled with the current name.
   - Typing updates the result.
   - Clicking the result returns to the editor with the new name.
   - Esc returns with no change.
   - The paper never appears in the inventory.
   - **Clicking the result works in survival with 0 XP levels.** The repair cost is set to 0, and the vanilla client may refuse a 0-cost pickup. If it does, set the cost to 1 or use another way to confirm.
   - The 50-character limit holds.
4. **Displays:**
   - The icon faces outward, and isn't backwards or mirrored, on all 6 faces. I picked yaw and pitch values without seeing them in game.
   - The name is readable under the icon.
   - Top and bottom icons turn with the player's facing.
   - Icons aren't black in a dark room.
   - Ranges are roughly 32 blocks for icons and 16 for names.
   - Icons come back after walking away until the chunk unloads, then returning.
5. **Recolor in a real contraption:** an observer and comparator next to the box, and a hopper pulling from it during Save (AC-2).
6. **Canvas/Folia with two players in different regions:** the editor lock, and one player saving while the other has the box open (the color is refused with "Box is in use", and the name and icon still apply).

## Recommended PRD changes

1. **§6.2 / §7.3, Paper drop path:** replace "copy in `BlockDropItemEvent`" with the built-in datapack. Paper registers it from a `PluginBootstrap` with `LifecycleEvents.DATAPACK_DISCOVERY`. The same trick works on Fabric with source path `components."minecraft:custom_data".PublicBukkitValues…`, for the Fabric owner to decide. **Risk:** another datapack that also overrides the shulker loot tables would replace ours. Document it, and have `/shulkerstickers inspect` warn about it.
2. **§6.1, add a block-only `face` key** (lowercase, e.g. `south`) for `side` icons. The current keys say only "side", which isn't enough to redraw a box that hasn't moved. It isn't copied to items, since it's recomputed on placement.
3. **§6.2, dispenser placement on Paper:** `BlockPlaceEvent` doesn't fire, so the item's data lands only in the block entity's vanilla `custom_data` component, which the Paper API can't read. Handle `BlockDispenseEvent` (copy the data after placement), or add a second set of `copy_custom_data` ops from `components."minecraft:custom_data"` so the data at least survives the next break. **Not implemented in the spike.**
4. **§8:** remove "*(verify datapack tags on Paper)*". Tags work when the plugin ships them, so the blocklist tag can work on both platforms.
5. **§7.4 baseline:** Paper 26.3 is **stable** (build 248), not beta. Folia 26.3 is alpha (build 6). Canvas is stable (build 1010). run-paper 3.1.0 works with Gradle 9.9.1. Canvas needs our own `runCanvas` task (in `paper/build.gradle.kts`).
6. **§7.4 tests:** use MockBukkit for logic, plus a real-server self-test (Paper, Folia, Canvas) for loot, observers, displays and threading. MockBukkit can't cover those.
7. **§5.4:** confirm the anvil repair-cost behavior with a client (manual check 3) before promising "free".

## Notes for merging with the Fabric spike

- **Move to `core`:** the key names (`StickerKeys`), the editor slot layout and click rule (`EditorLayout`), and the 24-char name shortening. They're platform-free.
- **Format differences to settle:**
  - When `icon_visual` is empty, Paper writes `"{}"`; Fabric omits the key and reads it as `""`.
  - Fabric ignores data whose `v` isn't 1; Paper reads it anyway.
  - Only Paper has the `face` key.
- **Datapack format:** `pack.mcmeta` uses `min_format: 121`, `max_format: 200` (26.3's data format is 121.0). Check it at each Minecraft update.
