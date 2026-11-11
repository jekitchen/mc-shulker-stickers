# Paper/Canvas 0.1 notes (plan Tasks 8 and 10)

Checked 11 Nov 2026 on Paper 26.3 build 248, Folia 26.3 build 6 (alpha) and Canvas build 1010, with paper-api 26.3.build.248-stable and MockBukkit 4.120.0.

## Results

- **Unit tests (MockBukkit):** 51 pass, 0 failures, 0 skipped (`./gradlew :paper:build`).
  - `StickerStoreTest` 11, `FeaturesTest` 12, `EditorFlowTest` 9, `StickerTransferListenerTest` 6, `FacesTest` 6, `IconItemsTest` 5, `BoxRecolorerTest` 2.
  - Some of `FeaturesTest` was written after the code. To check it, I added two bugs on purpose (skip the XP charge; allow adventure mode). Both made the tests fail, and both were reverted.
- **Real-server self-test** (`./gradlew :paper:runServer|runFolia|runCanvas -Pselftest`):

| Server | Build | Result | Errors / thread-check failures |
|---|---|---|---|
| Paper | 26.3 build 248 | 18/18 | 0 / 0 |
| Folia | 26.3 build 6 (alpha) | 18/18 | 0 / 0 |
| Canvas | build 1010 | 18/18 | 0 / 0 |

The self-test now also covers:
- Icon visual capture and rebuild.
- Unreadable visuals draw nothing.
- `face` and `name_hidden` survive drops.
- Dispenser placement.
- A hidden name isn't drawn.
- A name with no sticker data is drawn on the lid, truncated.
- Translations load.

One Folia failure along the way was the self-test's fault, not the plugin's. The run directory keeps its world between runs, so a box from an earlier run kept its old block entity. The test now clears the block to air first.

## Dyed-helmet `icon_visual` (for the Fabric comparison)

A leather helmet dyed `0xFF0000` that also has lore stores:

```
{"minecraft:dyed_color":16711680}
```

It comes from `Bukkit.getUnsafe().serializeItemAsJson(item)` → `components` → `IconVisuals.canonicalize`. The lore is dropped. When the box is drawn, the item is rebuilt with `deserializeItemFromJson({"id","count":1,"components",DataVersion})`, and the self-test confirms the color comes back as `0xFF0000`. Same string on Paper, Folia and Canvas.

## What's done

**Task 8: the Paper module now uses core.** Core provides:
- Data: `StickerFormat`, `Sticker` and `StickerCodec`. Keys are `shulker_stickers:<short>`, with ints as INTEGER and strings as STRING. An empty `icon_visual` is omitted.
- Rules: `Faces`, `EditorLayout`, `BoxState` with `iconVisual`, and `rules.*` (NameRules, BlockedWords, IconRules, IconVisuals, Costs).
- Text and config: `Translations`/`TextKeys`, using `player.locale()`, and `ConfigLoader` with `plugins/ShulkerStickers/config.json`, created with defaults.
- `Permissions`, declared in `paper-plugin.yml` with defaults: true for color, icon, name and name.display; op for admin.

Paper's own `StickerData`, `StickerKeys` and `EditorLayout` are deleted. The plugin jar bundles core's classes and `shulker_stickers/lang/en_us.json` (checked).

**Task 10, items 1–11:**
1. **Icon visual properties:** captured and drawn as described above.
2. **Blocklist and size cap:** the config IDs, `#tag` entries in the config, the bundled `#shulker_stickers:icon_blocklist` tag and `icon.max_data_bytes` are checked at three points: "Use off-hand item", Save, and every draw.
3. **Names:**
   - Display uses `NameRules.displayText`, `name.view_range` and `name.display_enabled`.
   - A player without `shulker_stickers.name.display` still saves the name, but `shulker_stickers:name_hidden`=1 is set and the name isn't drawn.
   - Blocked words are checked at Save.
   - When Save writes a name and the box has no sticker, a name-only sticker records the anchor and face. A name with no sticker data (from a real anvil) is drawn on the lid.
4. **Hide while open:** `InventoryOpenEvent` removes the displays, and `refresh` draws nothing while the box has viewers. `InventoryCloseEvent` redraws 10 ticks later, about when the lid finishes closing. The lid animation itself isn't read.
5. **Locking:** one editor per box, and a second player gets `deny.in_use_editor`. The lock is released on close, quit, death, world change and walking out of reach.
6. **Access checks:**
   - Spectators are always refused. Adventure mode is refused unless the config allows it.
   - Locked boxes are refused. Paper has no API to test a key, and vanilla needs the key in the main hand, which the gesture requires to be empty.
   - Vanilla spawn protection is applied: overworld only, ops exempt, square radius.
   - Without any of the color, icon or name permissions, the editor is refused.
   - A switched-off feature (config or permission) shows a grey "Turned off on this server" button, and clicks on it do nothing.
   - Claims: see "Protection approach".
7. **Costs:** shown on Save and checked before Save; the message is `save.cannot_pay`. They're charged on the player thread before the box changes. If the region-thread apply fails (box gone, recolored or locked), they're refunded. Creative players pay nothing.
8. **Closing the editor:**
   - It closes and discards when the player is more than block interaction range + 1 from the box (checked every 10 ticks on the player's scheduler), on quit, death and world change.
   - Esc shows `editor.discarded` only if something was staged.
   - Save with nothing staged just closes.
9. **Help and toggle:**
   - First-place help: a player PDC flag (`shulker_stickers:help_seen`). The tip shows once, if `help.first_place_message` is on.
   - The gesture toggle is stored in the player's PDC as `shulker_stickers:gesture_off`.
10. **Commands:** `/shulkerstickers help|toggle|inspect|clear [icon|name|all]|reload`, registered through `LifecycleEvents.COMMANDS` (Brigadier).
    - Inspect and clear read the target block on the player's thread, then do the work on the box's region and reply through the player's scheduler.
    - `clear icon` keeps the name and where it's drawn.
    - `reload` redraws known displays and reports the number of warnings.
11. **Dispenser placement:** `BlockDispenseEvent`, then a region task 1 tick later copies the item's data onto the new box. A side icon goes on the side facing back toward the dispenser. If that face isn't a side, it goes north, else on the first side. Verified on all three servers.

Drops: the built-in datapack's 17 loot tables now also copy `face` and `name_hidden`.

## Protection approach (claims)

**Decision:** for 0.1, claim plugins are honored only through the `PlayerInteractEvent` they already cancel. The gesture listener runs at `HIGH` and refuses when `useInteractedBlock() == DENY`, so any plugin that denies the right-click at LOW, NORMAL or HIGH blocks the editor. I don't fire fake `BlockBreakEvent` or `BlockPlaceEvent` events, because logging plugins such as CoreProtect record them at MONITOR (with `ignoreCancelled`) and would log edits that never happened.

**Gap:** a claim that allows "container access" but not "build" lets the click through, so PRD §9's "container access but not build → refused" isn't met for those plugins. The PRD already moves claim integrations to 0.2. The clean fix is soft hooks for each plugin's own API (WorldGuard `RegionQuery#testBuild`, GriefPrevention `Claim#checkPermission(..., ClaimPermission.Build)`, Lands `LandWorld#hasRoleFlag`, HuskClaims `canBuild`/operation types). Each would be compile-only and loaded only if the plugin is present.

Claims are also not re-checked at Save (only when the editor opens). The editor closes once the player walks out of reach, so this window is small.

## Manual checks with a vanilla 26.3 client

Run `./gradlew :paper:runServer` (and `:paper:runCanvas`), join with an unmodded 26.3 client, then:
1. Gesture, editor and the AC-12 click types, as in `docs/spike/m0-paper.md`.
2. **Rename screen:** type a name and click the result slot. Check that it works at repair cost 0, and that the anvil's paper never reaches the inventory.
3. **Icon visuals:** use a dyed leather helmet, an enchanted sword (glint), a player head and a banner in the off-hand. Each icon should look like the item.
4. **Hide while open:** open the box normally. The icon and name disappear while it's open and come back after it closes.
5. **Costs:** set `"color": {"cost": {"type": "xp_levels", "amount": 1}}` and run `/shulkerstickers reload`. Save shows the cost; with 0 levels Save is refused; in creative it's free.
6. **Name display permission:** deny `shulker_stickers.name.display` (LuckPerms) and rename a box. The name is saved (GUI title) but not drawn. `/shulkerstickers inspect` shows `name_hidden = 1`.
7. **Commands:** `help`, `toggle` (the gesture now opens the box normally), `inspect`, `clear icon|name|all`, `reload`.
8. **First placement tip:** place a box with a new player; the tip appears once.
9. **Out of reach:** with the editor open, walk away (or be pushed). The editor closes and "Changes discarded" shows.
10. **Dispenser:** a dispenser places a stickered box, and the icon shows on the side facing the dispenser.
11. **Language:** with the client set to another language, strings show in English, not as raw keys.

## Move to core

- The `name_hidden` key name (now `PaperKeys.NAME_HIDDEN`); the Fabric agent uses the same string.
- The player-data key names `help_seen` and `gesture_off`, if Fabric wants the same names.
- `EditorService.isButtonPress`: the "plain left or right click on a menu slot" rule is platform-free apart from the click enum.
- The dispenser side rule (`StickerTransferListener.dispenserSide`) works in core `Face`s.
- Cost formatting (`Payments.describe`).

## PRD additions suggested

1. **§6.1:** add `name_hidden` (int, 1 = the name isn't drawn) and `face` (exact face of the icon on a placed box) to the key table. Say that the stored `icon_visual` is compact JSON (keys sorted, absent when empty), not SNBT.
2. **§9, locked box:** the gesture needs an empty main hand and vanilla needs the key in the main hand, so locked boxes can't be edited. Either accept that or change the rule (e.g. key in the off-hand).
3. **§7.3 / §9, protection:** document the 0.1 approach (honor cancelled interact events, no fake block events) and that per-plugin build checks arrive in 0.2.
4. **§5.5, hide while open:** Paper hides on open/close events and redraws about 10 ticks after the last viewer closes.
5. **§5.4 / §8:** say that a name saved without `name.display` is kept but hidden, so the box keeps its name if the permission is granted later.

## Not done

- Per-plugin claim hooks (0.2, see above).
- Re-checking claims at Save.
- Using the lid animation state (open/close events are used instead).
- Reach is checked every 10 ticks rather than every tick.
- The rename screen and icon orientation still need a real client.

## Review fixes (11 Nov 2026)

Fixes for the Paper code review. New tests are in `SaveSafetyTest` (16 tests); the self-test has 3 new checks.

**Save**
- **One-shot.** Pressing Save sets `Session.saving` and captures a `SaveRequest` snapshot on the player thread. Later presses and Esc are ignored until the save finishes, and the region task only reads the snapshot.
- **Charged after applying.** `finishSave` charges, on the player thread, only for the parts `apply` reports as applied, at the prices from when Save was pressed. A color change refused because the box is open costs nothing. Nothing is charged if the save fails or the player logs out first. So there are no refunds to lose; the cost of that is that a logout in the 1-tick window means a free edit.

**Data**
- **Unreadable data is kept.** A name-only save writes anchor and face only when the box has no sticker keys at all (`StickerStore.hasData`). Data this version can't read (e.g. `v=99`) is replaced only when the player changes the icon.

**Box identity**
- **A box replaced during an edit counts as gone.** Break, destroy, block/entity explosion and piston events on a box being edited set `Session.boxGone`. The editor then closes with "The box was moved or broken", and a pending Save is refused, writes nothing and charges nothing.
- **No new persisted key.** `/setblock` and WorldEdit fire no event; for those, the existing position-and-color check still applies.

**Dispensers**
- **Only a box the dispense placed gets the sticker.** The target must be empty or replaceable at dispense time, and one tick later it must be a box of the dispensed type with no data. A box already standing there is never labelled.

**Disable and threading**
- **Plugin disable.** `onDisable` closes every editor and rename screen (so the menu can't become a free chest), releases locks, and removes all displays.
- **Disable on Folia/Canvas.** There a plugin is only disabled at shutdown and there's no world-wide entity access, so players and entities not owned by the disabling thread are skipped, and so is the stray sweep. That is safe because displays are never saved.
- **Menu switches are deferred.** Rename, Cancel, rename-confirm and the empty Save open or close menus on the player's next tick, not inside `InventoryClickEvent`.
- **Same-player reopen** on the same box keeps its lock.
- **A cancelled menu open** (another plugin) ends the session.

**Input rules**
- **All editing off means vanilla.** If color, icon and name editing are all off in the config, the gesture is vanilla and the box opens normally.
- **Rename text is filtered** like vanilla's `StringUtil.filterText` (no `§`, no control characters), as a second line of defense after vanilla's own filter.

**Tests**
- **AC-12:** MockBukkit tests now cover number keys, the off-hand swap, double-click, drag and clicks outside the window.

**Pushed back**
- **"Keep the editor open when apply fails."** Apply-time failures are exactly PRD §9's cases (box broken, moved, changed or locked), and §9 says the editor closes. Pre-apply refusals (cost, blocked word, blocked icon) already keep it open.
- **"Send a comparator update after recolor."** The Paper API has no analog-output-only update, and a neighbor update would fire observers (AC-16). A comparator's output depends only on the contents, which a recolor doesn't change, so the signal stays correct.

## Parity review fixes (Paper now matches Fabric)

From the cross-platform parity review, each with a test (`ParityTest`, `IconOrientationTest`, or a self-test check):

- **#3 Remove icon on a named box** keeps the existing anchor, face and rot, so the name stays where it was drawn.
- **#5 Removing the name from a name-only box** (editor or `clear name`) removes `v`, `anchor`, `rot` and `face` too. Data this version can't read is still never touched unless the icon changed.
- **#6 `rot`** is stored only for content on the top or bottom face, and is 0 elsewhere. A new icon takes the player's facing when "Use off-hand item" is clicked; a name-only sticker takes it at Save.
- **#8 Protection:** when a protection plugin cancels the gesture's right-click, the player now sees `deny.protected` on the action bar. Save re-checks game mode as well as permissions, lock and that the box is the same one.
- **#9 Reload** redraws every loaded box with sticker data or a name, including ones that drew nothing before.
- **#12 Unparseable `icon_visual`** draws nothing and keeps the data, instead of drawing the plain item.
- **#13 A box locked during the edit:** Save is refused with `deny.locked` and the editor stays open, so the player can try again. This changes the earlier "pushed back" item above for this case only; a box that's gone still closes the editor.
- **#15 A config file that can't be read on reload** keeps the current settings and reports a warning, instead of falling back to defaults.
- **Canvas shutdown error:** removing displays at disable no longer asks for another region's entity location. It uses `Bukkit.isOwnedByCurrentRegion(entity)` and skips anything it can't safely touch, because displays are never saved. The self-test runs on Paper, Folia and Canvas end with no errors.

## Orientation convention (both platforms)

A display with no rotation shows its image facing +Z (south), with the image's top toward +Y. Paper now leaves the display's yaw and pitch at 0 and sets the transformation's left rotation (`IconOrientation`). The icon and name offsets move along the image's own up direction:

| Face | Image faces | Image top points |
|---|---|---|
| North, south, east, west | out of the face | up |
| Top | up | the way the player faced when the icon was applied: `rot` 0 = south, 1 = west, 2 = north, 3 = east |
| Bottom | down | the same world direction as on the top face |

`rot` is `floorMod(round(yaw / 90), 4)` with Minecraft yaw (0 = south, 90 = west). The top face reads like an item in a floor item frame seen by that player. `IconOrientationTest` checks the image normal and image up for all 6 faces and 4 rotations. The assumption that an unrotated display's image faces +Z still needs a check with a vanilla client.

## Displays only near players (AC-8)

See `perf-ac8.md`. Displays now exist only while a player is within view range; with no player near, the plugin's cost for 1,000 decorated boxes is about 0.04 ms/tick. The self-test forces "a player is nearby" through `DisplayManager.forceNearby(true)`, and adds a check that nothing is spawned when no player is near while the box stays tracked. A player in view of a whole 1,000-box wall still costs about 0.7 ms (2,000 entities); see `perf-ac8.md` for the options.

Test counts after these fixes: 84 unit tests (MockBukkit and pure). The self-test passes 23/23 on Paper 248, Folia 6 and Canvas 1010.

## Regression-review fixes

- **Payment is taken when Save is pressed**, on the player's thread, before the apply is scheduled. When the result comes back, whatever wasn't applied is refunded: everything if the Save was refused, the color part if the color was refused because the box was open. Before this, the charge came after the apply and only if the player could still pay, so pressing Esc and dropping the cost items, or logging out, made the edit free.
- **Refunds for players who left** are recorded (`PendingRefunds`, by player UUID) and paid when they next join. They are kept in memory only, so a refund owed across a restart is lost. That needs a logout in the one or two ticks between Save and its result, on a Save that was then refused, so 0.1 accepts it.
- **After Esc during a Save**, a refusal no longer leaves the session and its lock behind: if the editor isn't open any more, the session ends. A successful Save closes the player's menu only if it is still this editor.
- **Walking out of reach during a Save** no longer reports "Changes discarded": the Save is already paid for and finishes.
- **Displays:** a per-chunk index next to the tracked boxes, so each chunk's once-a-second check looks only at its own boxes. A check that finds its chunk unloaded forgets everything in it, and a late refresh for an unloaded chunk forgets the box instead of tracking it (or loading the chunk).

Tests: 98 unit tests (14 new in `RegressionFixesTest`, using item costs as well as XP). Self-test 23/23 on Paper 248, Folia 6 and Canvas 1010.
