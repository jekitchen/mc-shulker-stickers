# Fabric 0.1 notes (plan Tasks 8 and 9)

Checked 11 Nov 2026 on Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.0+26.3, Polymer 0.18.7+26.3.

## Summary

The Fabric module now uses `core` for the data format, faces, editor layout, config, translations, name/icon/cost rules and permission names, and it has every 0.1 feature from plan Task 9. **49 GameTests pass** (`./gradlew :fabric:build`, which runs `runGameTest`). It started at 13.

| Test class | Tests | Covers |
|---|---|---|
| `StickerDataGameTests` | 3 | Data layout, other plugins' keys kept, break → place |
| `RecolorGameTests` | 4 | AC-1, AC-16 (observer, comparator), box in use |
| `EditorMenuGameTests` | 5 | AC-12 every click type, staging, Save, Cancel, remove icon |
| `IconGameTests` | 10 | Visual properties, glint, untrusted data, blocklist (IDs, `#tags`, re-check when drawn), size cap, side face |
| `EditRulesGameTests` | 15 | Adventure/spectator, lock key, refused Save, editor locks, blocked words, name-only face, removing everything, feature toggles, XP and item costs, creative pays nothing, `clear` |
| `DisplayGameTests` | 9 | Icon + name drawn, name-only box, hidden name, hide while open, dispenser side, player side + first-run help, toggle |
| `SessionGameTests` | 3 | Walking away closes the editor and frees the lock; commands registered; admin-only subcommands |

Most of the Task 9 code was written before its tests. To check that the tests can fail, I broke the code on purpose and confirmed the matching tests failed, then restored it:
- blocklist off → 3 failures;
- payment always succeeds and lock check always passes → 3 failures;
- hide-while-open handler off and placement hook off → 2 failures.

## Cross-platform format checks

- **Red-dyed leather helmet** (`dyed_color` 0xFF0000, plus lore that must not be stored) stores exactly:

  `{"minecraft:dyed_color":16711680}`

  Test: `IconGameTests#dyedHelmetStoresCanonicalVisual`.
- **Enchanted items** store only `{"minecraft:enchantment_glint_override":true}`. Enchantments themselves aren't in the visual list, but the PRD lists "enchantment glint", so `Icons.visualOf` adds an explicit glint override when the stack is enchanted and has none. **Paper must do the same or the strings differ.** (`IconGameTests#enchantedItemKeepsGlintOnly`)
- **Encoding:** `DataComponentPatch.CODEC` with `registryAccess().createSerializationContext(JsonOps.INSTANCE)`, then `IconVisuals.canonicalize`.
- **Decoding:** `IconVisuals.parse` re-filters the data. It is then decoded with the same codec and applied with `ItemStack#applyComponents`. On any failure nothing is drawn and the data is kept.

## How the features work

- **Access** (`Access.denyReason`) is checked when the editor opens and again at Save, in this order:
  1. Spectator, or adventure mode without `allow_adventure_mode` → `deny.adventure`.
  2. `ServerLevel#mayInteract` (spawn protection, world border), plus Common Protection API `canPlaceBlock && canBreakBlock` when the `common-protection-api` mod is loaded → `deny.protected`.
  3. Vanilla lock → `deny.locked`.
  4. None of the color/icon/name permissions → `deny.permission`.
- **Lock key:** accepted in **either hand**. The gesture needs an empty main hand, so "the same as opening the box" (main hand only) would make locked boxes impossible to edit (PRD gap, below).
- **Permissions** (`Perms`):
  - With the Fabric Permissions API installed (mod id `fabric-permissions-api-v0`, compileOnly 0.7.0): player nodes default to true and `admin` defaults to `PermissionLevel.GAMEMASTERS`.
  - Without it: everyone has the player nodes, and admin uses `Permissions.COMMANDS_GAMEMASTER` (op 2).
- **Feature toggles and missing permissions:** buttons get a grey "Turned off on this server" lore line and do nothing.
- **Locks** (`EditorLocks`): keyed on dimension + position. Taken in `EditorMenu.tryOpen` and kept while the player is in the rename screen.
  - Released when the editor closes: by Save, Cancel, Esc, death, `stillValid` failing (out of reach + 1 block, or a different level, so dimension changes are covered), or the box being broken.
  - Also released on `ServerPlayConnectionEvents.DISCONNECT`.
- **Save** follows PRD §6.3:
  1. The box must still exist with its original color (`save.box_gone`, which closes the editor).
  2. Access is checked again.
  3. Blocked words.
  4. Icon size and blocklist.
  5. The player must be able to pay (`save.cannot_pay` with the cost text).
  6. Then: write the sticker, set the name and the `name_hidden` flag, recolor, charge, redraw.

  Steps 1–5 leave the editor open with the reason in the action bar. If recolor finds the box in use, the color isn't changed and isn't charged; the icon and name still apply.
- **Hidden names:** the name is saved, but if the saver lacks `shulker_stickers.name.display`, `shulker_stickers:name_hidden = 1` is written and the name isn't drawn. The key is now in core (`StickerFormat.NAME_HIDDEN`). Writing a sticker keeps this flag.
- **Name-only boxes:** when a name is saved with no icon, the sticker records the clicked face (anchor/face/rot) without `icon_id`. Removing the icon from a named box keeps that face for the name. A name with no sticker (e.g. renamed at an anvil) is drawn on the lid. With no icon, the name is centered on the face.
- **Hide while open:** a per-level end-of-tick pass over drawn boxes checks `getAnimationStatus() != CLOSED`, removes the holder, and re-shows it once the lid has closed. It is off when `display.hide_while_open` is false.
- **Costs** (`Payments`): XP levels and items are counted and taken from the main inventory. Creative players pay nothing. An unknown cost item can never be paid. The Save button's lore shows the cost.
- **Placement hook:** `BlockItemMixin` (`BlockItem#place`, RETURN) calls `StickerPlacement.onPlaced`. Fabric API has no "block placed from an item" event. `onPlaced`:
  - Moves a `side` icon to the side facing the player (`Faces.sideToward` from the eye position), or for a dispenser (no player) to the side facing back toward it (`Faces.dispenserSide`).
  - Redraws the box. Before this hook, a freshly placed box wasn't drawn until its chunk reloaded.
  - Shows the first-placement tip once.
- **Player prefs:** a Fabric data attachment `shulker_stickers:player_prefs` (`help_seen`, `gesture_off`), persistent and copied on death.
- **Commands:** `/shulkerstickers help | toggle | inspect | clear [icon|name|all] | reload`. `inspect` and `clear` act on the box under the player's crosshair (block interaction range). `reload` re-reads the config, redraws every drawn box, and reports how many warnings there were. Admin = `shulker_stickers.admin`.
- **Config:** `config/shulker_stickers.json`, loaded on `SERVER_STARTING`. Warnings are logged.
- **Text:** every player-visible string goes through `core` translations with `ServerPlayer#clientInformation().language()`. Only `en_us` exists so far.

## Needs a manual check with a vanilla 26.3 client

All of the spike's list (`docs/spike/m0-fabric.md`) still applies. New items:

1. Disabled buttons show the grey "Turned off on this server" line. The Save button shows "Costs …" when costs are set.
2. A name-only box shows its name centered on the clicked face (side, top or bottom) and readable.
3. Displays disappear when the box opens and come back after the lid closes, with no flicker on a quick open/close.
4. The first-placement tip appears once per player, survives a relog and death, and `/shulkerstickers toggle` really lets a block in the off-hand be placed against a box.
5. A box placed by a dispenser shows its side icon on the side facing the dispenser.
6. Fabric Permissions API (e.g. with LuckPerms) and a Common Protection API claim mod (e.g. Flan or GOML) on a real server. They're compileOnly and not installed in the GameTests, so those code paths have only been compiled, not run.

## Move to core

- `editor/Placement.clicked`: anchor, rot (top/bottom = the player's horizontal facing) and face from a clicked face. Paper needs exactly the same rule.
- The glint rule in `Icons.visualOf` (enchanted → `enchantment_glint_override: true`) belongs in `IconVisuals`, e.g. `canonicalize(components, boolean enchanted)`, so Paper can't drift.
- Order of the access checks (game mode → protection → lock → permission) and which deny key each one uses.
- The Save rules: "removing the icon keeps the face for a name", and "nothing left → remove the sticker" (`EditorMenu.stagedSticker`).
- The text for costs (`Payments.describe`) uses only core keys, so it could move too.

## PRD additions / changes

1. **§6.1 `name_hidden`** (int 1): the name was saved by a player without `shulker_stickers.name.display` (now `StickerFormat.NAME_HIDDEN`).
2. **§6.1 name-only sticker:** a sticker may have no `icon_id`; it then only records the face for the name.
3. **§6.1 `icon_visual`** is canonical JSON (see the plan). Enchanted items store an explicit glint override.
4. **§9 lock key:** the key counts in either hand. Otherwise locked boxes can't be edited at all, because the gesture needs an empty main hand.
5. **§5.4 names renamed at a real anvil** don't get `name_hidden`, even if the player lacks `name.display`. If that matters, it needs an anvil hook. Not done.

## Not done / limits

- Only English text.
- Editors that are open keep the config they started with until reopened.
- The hide-while-open pass visits every drawn box each tick. It's cheap per box, but it should be measured for AC-8 (1,000 boxes).
- Not tested with an automated test: the Esc "Changes discarded" message, the disconnect lock release, `inspect` output, and `reload`'s redraw.
- Lid animation (v0.2) isn't started.
