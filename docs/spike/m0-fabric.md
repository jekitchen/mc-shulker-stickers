# M0 spike: Fabric findings

Checked 11 Nov 2026 against Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.0+26.3, Loom 1.19.4, Polymer virtual entities 0.18.7+26.3, Java 25.

## Summary

The Fabric half of the spike works end to end on a server, with **13 GameTests passing** (`./gradlew :fabric:runGameTest`). Two of them were checked against the bug they guard: with the drop-copy hook removed, the break-and-place test fails; with normal block-update flags, the observer test fails.

| Area | Status |
|---|---|
| Gesture (sneak + empty main hand) | Done, not yet tried with a real client |
| Sticker editor (chest menu, staged Save/Cancel) | Done; Save, Cancel, Remove icon and Use off-hand are tested |
| Menu safety (AC-12) | Tested for every `ContainerInput` type × 11 slots × 6 buttons |
| Recolor swap (AC-1, AC-16) | Tested: all 17 variants, observer, comparator, box open |
| Sticker data in `custom_data/PublicBukkitValues` | Tested, including keeping other plugins' keys |
| Break → pick up → place | Tested (needed a drop hook; see below) |
| Polymer display of icon and name | Implemented; **needs a manual client check** |
| Rename screen (anvil, no block) | Implemented; **needs a manual client check** |

## How it's built

All code is in `fabric/src/main/java/io/github/jekitchen/shulkerstickers/fabric/`:

- `ShulkerStickersFabric`: registers the gesture (`UseBlockCallback`), the drop hook (`LootTableEvents.MODIFY_DROPS`) and the displays.
- `editor/EditorMenu`: a `ChestMenu` (`GENERIC_9x3`) on a throwaway `SimpleContainer`. It overrides `clicked` to act only on `ContainerInput.PICKUP` with button 0 or 1 on slots 0–26, and resyncs the client after every click. `quickMoveStack` returns empty, and `canDragTo` and `canTakeItemForPickAll` return false.
- `editor/RenameMenu`: an `AnvilMenu` built with `ContainerLevelAccess.NULL`. Clicking the result slot confirms, closing cancels, and the display-only paper is cleared in `removed()` so it never reaches the player.
- `Recolor`: the PRD §6.3 swap.
- `data/StickerData`: reads and writes the sticker keys.
- `display/StickerDisplays`: Polymer holders.

## Answers to the PRD §12 verify list

| Item | Answer | Evidence |
|---|---|---|
| Use-block event after the 26.1 renames | `UseBlockCallback.EVENT`, `(Player, Level, InteractionHand, BlockHitResult) -> InteractionResult`; return `PASS` to let vanilla run | Fabric API 26.3 source; used in `ShulkerStickersFabric` |
| Server-side anvil menu receives the rename text | Yes. `ServerGamePacketListenerImpl.handleRenameItem` calls `AnvilMenu#setItemName(String)` when `player.containerMenu instanceof AnvilMenu` and `stillValid` is true. With `ContainerLevelAccess.NULL`, `ItemCombinerMenu#stillValid` is always true, so no anvil block is needed | 26.3 source. **Not yet seen with a real client** |
| A block entity keeps `custom_data` | Yes. `BlockEntity#applyComponents` keeps every non-implicit component in `components`, which is saved under `"components"` | `BlockEntity.java`; test `stickerIsStoredInPublicBukkitValues` |
| Shulker loot table copy function | `data/minecraft/loot_table/blocks/*shulker_box.json` uses `minecraft:copy_components` from `block_entity`, including **only** `custom_name`, `container`, `lock` and `container_loot`. **`custom_data` is dropped.** The 26.x loot format calls this a `"modifier"`, not `"functions"` | Server jar; fixed with `MODIFY_DROPS` (test `stickerSurvivesBreakAndPlace`) |
| Creative-mode break | `ShulkerBoxBlock#playerWillDestroy` builds the drop from `collectComponents()`, so it already keeps `custom_data` (only for non-empty boxes; vanilla drops nothing for an empty one) | `ShulkerBoxBlock.java` |
| Polymer 26.3 build | `eu.pb4:polymer-virtual-entity:0.18.7+26.3` resolves, loads, and its `ChunkAttachment`/`ItemDisplayElement`/`TextDisplayElement` API matches | Build and GameTest server start |
| Anvil keeps `custom_data` | Yes, `AnvilMenu#createResult` starts from `input.copy()` | `AnvilMenu.java` |
| Dye recipe keeps `custom_data` | Yes, the recipe is `minecraft:crafting_transmute`, and `TransmuteRecipe#computeResult` uses `createWithOriginalComponents` | `recipe/red_shulker_box.json`, `TransmuteRecipe.java` |
| Cauldron wash keeps `custom_data` | Yes, `CauldronInteractions#shulkerBoxInteraction` uses `itemInHand.transmuteCopy(Blocks.SHULKER_BOX, 1)`, which keeps the full component patch | `CauldronInteractions.java`, `ItemStack#transmuteCopy` |
| Shulker boxes can't go in bundles | Correct, and they can't nest in other boxes either. `BlockItem#canFitInsideContainerItems()` is false for `ShulkerBoxBlock`, and `BundleContents` and `ShulkerBoxSlot#mayPlace` both check it | `BlockItem.java`, `BundleContents.java` |
| The server can tell when a box is open | `ShulkerBoxBlockEntity#getAnimationStatus()` (CLOSED/OPENING/OPENED/CLOSING), but it **lags a tick** behind `startOpen` because it's set by a block event. `openCount` is private. The spike checks `getAnimationStatus()` plus any player whose `containerMenu` is a `ShulkerBoxMenu` on this box (`Boxes#isInUse`) | Test `recolorRefusedWhileBoxIsOpen` failed with the animation check alone |
| Rename length limit | `AnvilMenu.MAX_NAME_LENGTH = 50` | `AnvilMenu.java` |

## Other 26.3 findings

- **Recolor flags.** `Block.UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | UPDATE_SKIP_ON_PLACE`:
  - No neighbor updates.
  - No shape updates, so observers don't fire.
  - The old block entity's removal side effects are skipped.
  - Then `level.updateNeighbourForOutputSignal` refreshes comparators.

  The data round trip is `saveWithoutMetadata` then `loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, ...))`.
- **Renaming a placed box.** There's no setter. The name only enters through `BlockEntity#applyComponents`, which also rewrites the contents and lock from the component view. `BoxEdits#setName` passes the full `collectComponents()` back in with only the name changed. Tests confirm the contents, lock and sticker survive.
- **API renames hit during the spike:**
  - `ClickType` → `ContainerInput`
  - `EntityType.ITEM` → `EntityTypes.ITEM`
  - Colored items are now collections, e.g. `Items.CONCRETE.pick(DyeColor.RED)`, `Items.STAINED_GLASS_PANE`, `Items.DYE`
  - `ResourceLocation` → `Identifier`
  - `LootContext#getOptional(LootContextParams.BLOCK_ENTITY)`
- **Test harness gotchas:**
  - The default GameTest structure has no floor, so blocks that need support pop off on the first shape update.
  - `makeMockServerPlayerInLevel()` players can open real menus.

## Not done in the spike

- Hiding displays while the box is open, and the lid animation.
- Locking (one editor per box), permissions, claims, adventure mode, the vanilla `lock` check, and costs.
- Closing the editor when the player walks away is only through vanilla `stillValid` (block interaction range + 1).
- Server-side translation: all strings are English literals.
- Icon visual properties, the blocklist and the 2 KB cap: only the item ID is stored and drawn.
- First-run help and `/shulkerstickers` commands.
- A name on a box with no icon is not drawn (see PRD changes).

## Manual checks needed with a vanilla 26.3 client

Run `./gradlew :fabric:runServer`, join with an unmodded 26.3 client, place a box, hold cobblestone in the off-hand, then:

1. Sneak + right-click the box with an empty main hand. The editor opens, titled "Edit: Shulker Box".
2. Try to take, shift-click, number-key swap, F swap, double-click, drag, and drop items out of the editor. Nothing moves, and no ghost items stay on the cursor or in the inventory.
3. Click a color, then Use off-hand item, then Save. The box changes color, the off-hand still holds the cobblestone, and a cobblestone icon appears on the face you clicked:
   - It sits just outside the face, the right way up, lit from the space in front.
   - On the top face it turns with your facing.
   - Report whether the icon shows its front or its back. The rotation in `StickerDisplays#faceRotation` assumes the front faces +Z.
4. Rename…: an anvil screen opens with "Rename box" and a paper item. Type a name and click the result slot:
   - The editor reopens with the name in the preview.
   - Save shows the name under the icon (~0.3 scale, shadowed, readable, not mirrored) and in the GUI title.
   - Check the anvil shows no XP cost, and check what the client shows on the result slot when the cost is 0.
5. Esc in the editor and Esc in the rename screen: nothing changes, and "Changes discarded" shows when something was staged.
6. Break the box, pick it up and place it again: the icon and name come back. Repeat in creative mode with a non-empty box.
7. Sneak + right-click with a block in the off-hand and an empty main hand: the editor opens and no block is placed (the PRD's known overlap).
8. Leave the area and come back, and restart the server: the displays come back with no leftover entities (`/kill @e[type=item_display]` should find nothing persistent).

## Recommended PRD changes

1. **§7.4 / §12 toolchain:** Loom **1.19.4**, Loader **0.20.1**, Fabric API **0.166.0+26.3**, Polymer **0.18.7+26.3**, Gradle **9.7+** (the build uses 9.9.1).
2. **§6.1 data model, side anchor:** `anchor = side` doesn't say *which* side a placed box's icon is on. The spike reuses `rot` (0–3, index into the four faces perpendicular to `facing`). Either document that, or add a `side` key.
3. **§5.4 name without icon:** "centered on the face if there's no icon" needs a face, but nothing records one when only a name is set. Suggest storing the anchor whenever the name is set from the editor, or defaulting to the lid.
4. **§6.2 persistence matrix:** make "the vanilla loot table drops `custom_data`" a confirmed fact (Fabric fix: `LootTableEvents.MODIFY_DROPS`). The hook copies the whole `custom_data`, so other mods' data on boxes now survives breaking too. Call that out as intended.
5. **§5.2 locking:** "is the box open" must check open menus, not just the lid animation, which lags a tick.
6. **Move to `core`:** the sticker key names and format version (now in `fabric/.../data/StickerData`) and the side-index rule (`editor/DisplayFaces`), so Paper uses the same ones.
