# Shulker Stickers — PRD

Status: Draft v0.4 (M0 spike results applied; items marked *proposed* await the owner) · Owner: Jim Kitchen

## 1. Summary

Shulker Stickers lets Minecraft: Java Edition players organize shulker boxes visually. In one **Sticker editor** on a placed box, a player can **recolor it without dye**, **stick an item icon on it** (from the item in their off-hand, which is not consumed and needs no item frame) and **rename it**, then **save** all the changes at once. The name and icon are shown on the box.

It ships as **two builds that share one core**:

- a **Fabric mod**, for Fabric servers and singleplayer;
- a **Paper plugin**, for Paper and its forks. The same jar declares Folia support, so it also runs on **Canvas** (a Folia fork).

The server is authoritative in both builds. Vanilla clients see icons and names with nothing installed, and removing the mod or plugin leaves nothing behind in the world.

**Key decisions** (✅ = confirmed by Jim on 11 Nov 2026; everything else is proposed):

| Decision | Default | Why |
|---|---|---|
| ✅ Name and identifiers | **Shulker Stickers**; ID `shulker_stickers`; Modrinth/Hangar slug `shulker-stickers`; command `/shulkerstickers <param>` | One plural name everywhere players see it |
| ✅ Platforms | **Fabric** (mod) + **Paper** (plugin), and the same Paper jar on **Canvas/Folia**; all three ship at 0.1 | Paper and Canvas servers are where vanilla-client support matters most |
| ✅ Version policy | **26.3 and newer**; best effort to support each new release quickly | Fabric and Paper are both stable on 26.3 (Paper build 248); Canvas 26.3 is stable; Folia 26.3 is alpha |
| Toolchain | Java 25, Gradle 9.9.1; Mojang's own names (the game is unobfuscated). Fabric: Loom 1.19.4, Loader 0.20.1, Fabric API 0.166.0+26.3, Polymer 0.18.7+26.3. Paper: `paper-api` 26.3.build.248-stable | Checked in the M0 spike, 11 Nov 2026 |
| ✅ Dependencies | Fabric: Fabric API + Polymer only. Paper/Canvas: no dependencies beyond the server API | Fewer things for admins to install and fewer things to wait on at version updates |
| Rendering | Fabric: Polymer packet-only display entities. Paper/Canvas: real display entities marked as never saved | Vanilla clients see them; nothing is written to the world |
| ✅ Gesture | Sneak + right-click with an **empty main hand** opens the editor. The icon comes from the **off-hand** item | Main-hand use is left alone |
| ✅ Editor | Stays open; color, icon and name are staged; **Save** applies them all; **Cancel or Esc** discards | One coherent place to make every change |
| ✅ Rename | In the editor; free; no anvil, no name tag, no XP | The mod already isn't vanilla, so dropping the anvil's small XP cost isn't out of place |
| ✅ Name display | Small text under the icon on the same face, short view range | Readable in storage walls without overlapping the box above |
| Data format | Bukkit's persistent-data layout inside `minecraft:custom_data`, same on both platforms | Items move between Fabric and Paper servers with their stickers intact |
| ✅ Discoverability | A one-time chat message on a player's first shulker box placement; `/shulkerstickers help` shows it again | Otherwise nobody finds the gesture |
| MVP scope | Placed boxes only | Inventory overlays and inventory editing move to a v1.0 optional Fabric client companion |

## 2. Problem and prior art

A big shulker storage wall is hard to scan. Players have three options today, and each has a catch:

- **Dye the box at the crafting table.** It costs dye, needs a crafting step, and undoing it needs a cauldron.
- **Hang an item frame next to the box.** It adds an entity, needs an adjacent block, and stays behind when the box is moved.
- **Use an anvil-label mod.** Every change costs XP and a trip to the anvil.

None of these is free to change *and* travels with the box.

| Prior art | What it does | Gap / overlap with us |
|---|---|---|
| MrShulker / HerrShulker (fork for 1.21.10+) | Icon via the anvil (1 XP); sneak + right-click with dye recolors placed boxes | Uses up dye; icons visible only to clients that have the mod; 1.21.x only |
| Shulker+ | Icon via the anvil, also drawn in the inventory | Anvil and XP per change; dye needed |
| Paint your Shulkers! | Pixel-art painting editor opened with sneak + right-click, both hands empty | **Same gesture as ours**; designs stored and drawn only for the local player |
| Labelling Containers | GUI icon + text via a paper-consuming Label Printer | Placed containers only; consumable; no recolor |
| Shulker armor-trim mod (26.1–26.2) | Armor trims drawn on shulker boxes | Different way to tell boxes apart; needs a client mod to see |
| Another Dyeable Shulkers | Bedrock-style dyeing of placed boxes | Uses up dye |

**Differentiators:**

1. An icon from the item you're holding, free and not consumed.
2. Recolor in place with no dye.
3. One editor for color, icon and name, saved at once.
4. Visible to vanilla clients.
5. Runs on Fabric, Paper and Canvas servers.

**Competitor testing:** the test matrix includes running alongside the mods above where the platforms overlap (AC-14).

## 3. Goals, non-goals, success metrics

**Goals**

- **G1** Recolor a placed box to any of the 16 dye colors or undyed, with no dye, keeping everything inside it.
- **G2** Put an icon on a placed box from the off-hand item, without consuming it.
- **G3** Rename a box in the editor and show its name on the box.
- **G4** Color, icon and name survive every vanilla way of moving a box, and also moving an item between Fabric and Paper servers.
- **G5** Never cause data loss, including after the mod or plugin is uninstalled.
- **G6** Feature parity between the Fabric and Paper/Canvas builds for all server-side features.

**Non-goals**

- Multi-line labels or rich text.
- New blocks or items.
- Storage features such as sorting, search or a contents preview.
- Bedrock/Geyser support.
- A world-level database.
- More than one icon per box.
- NeoForge, Spigot or CraftBukkit (only Paper and its forks are supported).
- Minecraft versions before 26.3.

**Success metrics**

| Metric | Target |
|---|---|
| Bugs that lose items or box data | **0** (release gate) |
| Server cost with 1,000 decorated boxes loaded | < 0.5 ms/tick (spark) on each platform |
| Effort to label a box | One gesture plus 2–3 clicks |
| Time to support a new Minecraft release | Fabric: within 2 weeks of stable Fabric API. Paper: within 2 weeks of Paper's stable build (best effort) |
| Adoption (directional) | 5k downloads across Modrinth and Hangar in the first 90 days after v0.1 |

## 4. Users and user stories

**Personas:** the survival builder (singleplayer, Fabric); the player on an SMP server (Fabric, Paper or Canvas) using a vanilla client; the server admin.

| ID | Story | Priority |
|---|---|---|
| US-1 | As a player, I sneak-right-click a box with an empty main hand, pick a color in the editor and save, so I can color-code storage without hunting for dye. | MVP |
| US-2 | As a player holding cobblestone in my off-hand, I open the editor, choose "Use off-hand item", save, and the box shows a cobblestone icon. I keep my cobblestone. | MVP |
| US-3 | As a player, I replace or remove an icon in the editor. | MVP |
| US-4 | As a player, I rename a box in the editor, with no anvil, name tag or XP, and see the name on the box. | MVP |
| US-5 | As a player, I change the color, icon and name in one visit, then save once, or cancel and nothing changes. | MVP |
| US-6 | As a player, I break a decorated box, carry it or hopper it anywhere, place it again, and it looks the same. | MVP |
| US-7 | As a vanilla-client player, I see icons and names, and can use the editor, on Fabric, Paper and Canvas servers. | MVP |
| US-8 | As a new player, I'm told how the feature works the first time I place a shulker box, and can see it again with `/shulkerstickers help`. | MVP |
| US-9 | As an admin, I can turn each feature on or off, set costs, block icons and words, and inspect or clear a box. | MVP |
| US-10 | As an admin, I can move my server between Fabric and Paper without losing stickers on items. | MVP |
| US-11 | As a player with the Fabric client companion, I see icons on boxes in my inventory and hotbar. | v1.0 |
| US-12 | As a player, I decorate a box while it is still in my inventory. | v1.0 |

## 5. Interaction spec (identical on every platform)

### 5.1 Gestures on a placed box

| Player state | Result |
|---|---|
| Not sneaking | Vanilla behavior (the box opens) |
| Sneaking, main hand holds an item | Vanilla behavior (place the block or use the item). **Never intercepted** |
| Sneaking, main hand empty | Opens the **Sticker editor** on this box |
| Gesture turned off (`/shulkerstickers toggle`) or editor disabled | Vanilla behavior |

**The one remaining overlap:** in vanilla, sneak + right-click with an empty main hand and a *block* in the off-hand places that block against the box. On a shulker box, this opens the editor instead. `/shulkerstickers toggle` turns the gesture off for a player who needs that.

Feedback: a hand swing and the menu-open sound. Denials show a short action-bar message saying why (no permission, protected area, locked box, box being edited by another player).

### 5.2 Sticker editor (works on vanilla clients)

This is a server-side 3-row chest menu titled "Edit: ‹box name›". Every slot holds a display-only button, and no item can be taken out of it. **Changes are staged, not applied:** the box doesn't change until the player clicks Save.

- **Rows 1–2 (17 slots):** the 16 dyed boxes in creative-tab order, plus the undyed box. The staged color shows an enchantment glint.
- **Row 3:**
  - **Preview:** a shulker box item showing the staged color, name and icon (the icon is listed in its tooltip).
  - **Use off-hand item:** shows the off-hand item; clicking it stages that item as the icon. Greyed out if the off-hand is empty or the item is blocklisted.
  - **Remove icon:** stages removal of the icon.
  - **Rename…:** opens the text-input screen (§5.4).
  - **Cancel:** discards the staged changes and closes.
  - **Save:** applies everything in one server tick and closes. If costs are configured, they're shown on this button.

**Esc works the same as Cancel:** staged changes are discarded, and the action bar says "Changes discarded" if there were any. If Save fails, the editor stays open and shows the reason.

**Menu safety (anti-duplication):** the editor rejects every interaction that doesn't land on one of its buttons. That includes shift-clicking from the player's inventory, number-key and off-hand swaps, double-click collecting, dragging, and clicking outside the window. Each case has its own test (AC-12).

**Locking:** one editor per box at a time. Other players can still open the box normally. At Save, a color change is refused if anyone has the box open, and the other staged changes still apply.

**Text in the menus:** every string is translated on the server into the player's language before it's sent, because vanilla clients don't have our language files. English ships first.

### 5.3 Icon rules

- The icon is taken from the off-hand at the moment "Use off-hand item" is clicked, and only then. An off-hand totem or shield is never applied by accident. The off-hand stack is never changed.
- **Only what's needed to draw the icon is stored:** the item ID plus a fixed list of visual properties (custom model, item model, glint override, enchantment glint, dyed color, armor trim, head skin, banner patterns, potion color, firework visuals). Everything else is dropped, including containers, books and lore. This keeps icons small, prevents the "book ban" problem, and limits what breaks when Minecraft changes its item format.
- **Face:** the face the player clicked to open the editor; any of the six faces is allowed. On the top and bottom faces, the icon is turned to match where the player was facing when it was applied.
- Anything that fits in a vanilla item frame is allowed, except items on the blocklist (§8).
- **Untrusted data:** icons are re-checked every time they're drawn, not only when they're applied (blocklist, visual-property list, size cap of 2 KB of icon data). Boxes made with creative mode or `/give` can't bypass the rules.

### 5.4 Renaming and name display

- **Rename…** opens a server-side anvil-style text-input screen (vanilla clients have a text field there). It needs no anvil block and no name tag, and it's free. Confirming returns to the editor with the staged name; an empty name stages removing the name. The limit is the vanilla rename field's 50 characters *(verify)*.
- The name is stored in vanilla `custom_name`, so renaming at a real anvil still works.
- **Moderation:** an optional list of blocked words is checked at Save. A permission controls who can have their names shown on boxes, and admins can clear any name.
- It is drawn as a text display on the icon's face, under the icon, or centered on the face if there's no icon.
- Defaults: fixed billboard (flat on the face), about 0.3 scale, at most 24 characters shown with "…" after that, a view range of about 16 blocks, no background, and shadowed text.
- The vanilla GUI title shows the full name.

### 5.5 Display placement, light and lid

- **Light:** each display sits just outside the face, so it takes light from the open space in front of it. That avoids black icons. A config option draws icons always fully lit.
- **Lid:** a box's lid rises and twists when it opens. **MVP:** displays are hidden while the box is opening or open, and come back when it closes. **v0.2:** the lid-face icon moves along with the lid.

### 5.6 First-run help

The first time a player places a shulker box, they get a short chat message explaining the gesture and pointing to `/shulkerstickers help`. Whether they've seen it is remembered per player (Fabric: player data attachment; Paper: player persistent data). The message can be turned off in the config.

### 5.7 Boxes in the inventory

Not in the MVP. Both editing a box in the inventory and showing icons on items need a client mod. They are Fabric-only, in the v1.0 optional companion.

## 6. Data model and persistence

### 6.1 Format (identical on Fabric and Paper)

All sticker data lives in `minecraft:custom_data → PublicBukkitValues`. That is where Paper stores a plugin's persistent data, so the Fabric mod writes exactly what the Paper plugin would.

| Key (inside `PublicBukkitValues`) | Type | Meaning |
|---|---|---|
| `shulker_stickers:v` | int | Format version (starts at 1); we migrate it ourselves |
| `shulker_stickers:icon_id` | string | Item ID, e.g. `minecraft:cobblestone` |
| `shulker_stickers:icon_visual` | string | The visual properties from §5.3 as compact JSON: component ID → the value Minecraft's component codec produces, only the allowed components, keys sorted. Omitted when there are none. *(Changed from SNBT in v0.4: Paper's API can't produce SNBT for components, but both platforms can run the same codecs with JSON.)* |
| `shulker_stickers:anchor` | string | `lid`, `base` or `side` |
| `shulker_stickers:rot` | int | Icon rotation on the top and bottom faces (0–3) |
| `shulker_stickers:face` | string | *(New in v0.4.)* The exact face the icon is on for a placed box, lowercase (`north`). Needed because `side` alone doesn't say which side. Recomputed when the box is placed again |
| `shulker_stickers:name_hidden` | int | *(New in v0.4, proposed.)* 1 if the name was saved by a player without `shulker_stickers.name.display`; the name is kept but not drawn |

- **Color** is the block or item ID itself (17 vanilla variants). **Name** is vanilla `custom_name`.
- **Why not a custom component:** registered component types are synced to clients, and a vanilla client that receives an unknown type disconnects.
- **Why strings and a version:** Minecraft's automatic data upgrade doesn't reach inside `custom_data`. We store the item ID and SNBT text, and migrate them ourselves using the version number. If an icon can't be read, the raw data is kept and nothing is drawn, so a later fix can still recover it.
- **Placed boxes:** Paper keeps block-entity persistent data in the block entity's own `PublicBukkitValues`. Fabric keeps the `custom_data` component in the block entity (confirmed: `BlockEntity#applyComponents` keeps it and saves it under `components`). Items are portable either way. Whether a *placed* box keeps its stickers when a world moves between platforms is still open.
- **Name without an icon** *(proposed)*: when Save writes a name, it also writes `anchor` and `face` for the face that was clicked. A name with no anchor is drawn on the lid.
- **Anchor on re-placement:** `lid` is the face the box points to, `base` the opposite face and `side` one of the four in between. A `side` icon goes on the side facing the player who placed the box. For a dispenser, it goes on the side facing back toward the dispenser, or north if no side does.

### 6.2 Persistence matrix (must pass on both platforms)

| Path | Expected | Mechanism / risk |
|---|---|---|
| Break → pick up → place | Preserved | **Confirmed in M0:** the vanilla loot table copies only `custom_name`, `container`, `lock` and `container_loot`, so `custom_data` is lost. Fabric: `LootTableEvents.MODIFY_DROPS` copies it. Paper: a built-in datapack (registered at bootstrap) overrides the 17 shulker loot tables with a `copy_custom_data` step. **Risk:** another datapack overriding the same loot tables replaces ours |
| Piston pushes box / explosion | Box drops as item, data preserved | Same drop path |
| Hopper, dropper, ender chest, chests | Preserved | The stack moves with its components |
| Dispenser places box | Preserved; displays attached | Anchor rule from §6.1. Paper: `BlockPlaceEvent` doesn't fire for dispensers, so this needs its own handler |
| Creative pick-block (with data) | Preserved | Vanilla behavior |
| Anvil rename, dye recipe, cauldron wash | Preserved | Confirmed: the anvil starts from `input.copy()`, the dye recipe is a transmute recipe, the cauldron uses `transmuteCopy` |
| Bundles, nesting in another shulker | N/A | Confirmed: `canFitInsideContainerItems()` is false for shulker boxes |
| `/clone`, structure blocks, WorldEdit (Paper) | Preserved | Displays re-attached on chunk load |
| Item moved between Fabric and Paper servers | Preserved | Identical format (§6.1) |
| Minecraft version upgrade (26.3 → 26.4) | Preserved | Our own versioned format; tested with a world upgrade |
| Mod/plugin uninstalled | Contents, name and color intact; icon kept invisibly in `custom_data` | No saved entities, so nothing is orphaned. Reinstalling brings the icons back |

### 6.3 Save transaction (server only)

1. Check that the box still exists at the same position, and that the player still has permission (including the box's key if it has a vanilla `lock`) and can pay the cost. If not, refuse and keep the editor open.
2. Apply the name and the icon to the block entity.
3. If the color changed and nobody has the box open: save the block entity's full data (items, name, lock, loot table, components and persistent data).
4. Swap in the new color's block with the same `facing`, **without notifying neighbors**, so observers don't fire and redstone machines aren't triggered. Then send only the update that refreshes comparators.
5. Load the saved data into the new block entity, mark it changed, charge the cost, and refresh its displays.
6. All of this happens inside one tick on the thread that owns that region (on Folia/Canvas, the region thread), so no hopper can act in the middle. Tests cover duplication and loss.

## 7. Architecture

### 7.1 Repository layout (one repo, three modules)

| Module | Contents | Depends on |
|---|---|---|
| `core` | Platform-free Java: data format and migrations, icon validation, editor state machine (staging, Save/Cancel), menu layout, config model, translations, command tree | Nothing (JDK + Gson) |
| `fabric` | Fabric mod: events, mixins, Polymer rendering, menus, GameTests | `core`, Fabric API, Polymer |
| `paper` | Paper plugin (`folia-supported: true`): listeners, display entities, menus, schedulers | `core`, Paper API |

Only `core` holds the gameplay rules, so the two builds behave the same.

### 7.2 How icons and names are drawn

| | Fabric | Paper / Canvas |
|---|---|---|
| Mechanism | Polymer virtual entities (packets only) | Real `ItemDisplay`/`TextDisplay` entities, marked never to be saved |
| Lifecycle | Holder attached when the chunk loads | Spawned when the chunk loads, removed on unload, re-created as needed |
| Leftovers after uninstall | None | None (they are never saved) |
| Extra dependency | Polymer | None |

**Folia/Canvas threading:** all world and entity work runs on the region scheduler for the box's location; player messages and menus use the player's entity scheduler. Nothing uses the global main thread. Canvas loads only plugins that declare Folia support, so the jar declares `folia-supported: true`, and the plugin must actually be safe under region threading.

**Optional Fabric client companion (v1.0):** draws icons on the client and on inventory items. When a client has it, the server stops sending virtual entities to that player.

### 7.3 Components (both platforms)

- **Interaction:** sneak + empty main hand on a shulker box. Fabric: a use-block event. Paper: `PlayerInteractEvent` for the main hand, honoring cancellation by protection plugins.
- **Editor and rename screen:** hand-rolled on both platforms (Fabric: server-side chest and anvil menus; Paper: the inventory and menu APIs, plus Paper's anvil view for the rename text *(verify)*). No GUI library.
- **Display manager:** attach, refresh, hide while open, remove on break.
- **Protection:** Fabric uses vanilla checks plus Common Protection API if present. Paper respects cancelled interact events and runs a build-permission check that claim plugins (WorldGuard, GriefPrevention, Lands, HuskClaims) honor *(verify the method per plugin)*. **Editing needs build permission, not just container access.**
- **Config and commands:** the schema and command tree live in `core`. Brigadier commands on both platforms.

### 7.4 Technical baseline (checked 11 Nov 2026)

| Item | Value |
|---|---|
| Minecraft | 26.3 stable; 26.4 in snapshots (26.4-snapshot-8 on 10 Nov 2026) |
| Java | 25 (minimum since 26.1) |
| Fabric | Loom 1.19.4 (`net.fabricmc.fabric-loom`), Gradle 9.9.1 (run-paper needs 9.7+), Loader 0.20.1, Fabric API 0.166.0+26.3, Polymer `polymer-virtual-entity` 0.18.7+26.3 |
| Paper | `paper-api` 26.3.build.248-stable (26.3 is stable). Folia 26.3 build 6 is alpha |
| Canvas | A Folia fork; plugins need `folia-supported: true`. **26.3 build 1010 is stable** and loads the plugin (M0) |
| Repo | [jekitchen/mc-shulker-stickers](https://github.com/jekitchen/mc-shulker-stickers) (MIT; three-module Gradle build; CI on GitHub Actions) |
| IDs | Mod/plugin ID `shulker_stickers`; plugin name `ShulkerStickers`; Java package `io.github.jekitchen.shulkerstickers` (confirmed) |
| Tests | `core`: JUnit. Fabric: GameTest (`runGameTest`). Paper: MockBukkit `mockbukkit-v26.3` 4.120.0 for logic, plus a real-server self-test on Paper, Folia and Canvas for loot, observers, displays and threading. Manual vanilla-client checks |

**Client vs. server:** both builds run only on the server. The Fabric build also works in singleplayer, where the game runs its own built-in server. A client install is needed only for the optional v1.0 companion.

**Licenses:** Polymer is LGPL-3.0. Bundling it unmodified inside our MIT jar is normally fine; we include its license notice and a link to its source. Paper and Canvas are only compiled against, not bundled.

## 8. Config, permissions and commands

Config file: `config/shulker_stickers.json` (Fabric) or `plugins/ShulkerStickers/config.json` (Paper). Same schema on both. Reload with `/shulkerstickers reload`.

| Key | Default | Notes |
|---|---|---|
| `color.enabled` / `icon.enabled` / `name.edit_enabled` / `name.display_enabled` | `true` | Turn each feature on or off |
| `color.cost`, `icon.cost`, `name.cost` | `{type:"none"}` | Or `xp_levels` / `item` with an amount; charged on Save only for what changed |
| `icon.blocklist` | `[]` plus the tag `#shulker_stickers:icon_blocklist` | The tag works on both platforms (Paper: shipped in the plugin's built-in datapack) |
| `icon.max_data_bytes` | 2048 | Icons larger than this aren't drawn |
| `icon.view_range` / `name.view_range` | 32 / 16 blocks | |
| `icon.full_bright` | `false` | Draw icons fully lit |
| `name.max_display_chars` | 24 | Characters shown on the box |
| `name.blocked_words` | `[]` | Plain words match whole words, ignoring case; entries wrapped in slashes (`/b[a4]d/`) are regular expressions *(proposed)* |
| `display.hide_while_open` | `true` | |
| `help.first_place_message` | `true` | |
| `allow_adventure_mode` | `false` | |

**Permissions:** Paper uses the server's own permission system (LuckPerms and others work automatically). Fabric uses the Fabric Permissions API if it's installed, otherwise op levels.

| Node | Default |
|---|---|
| `shulker_stickers.color`, `.icon`, `.name` | Everyone |
| `shulker_stickers.name.display` | Everyone (names from players without it aren't shown on boxes) |
| `shulker_stickers.admin` | Op level 2 |

**Commands:** all are `/shulkerstickers <param>`.

| Command | Who | What it does |
|---|---|---|
| `/shulkerstickers help` | Everyone | Shows the how-to message |
| `/shulkerstickers toggle` | Everyone | Turns the gesture off or on for yourself |
| `/shulkerstickers inspect` | Admin | Shows the sticker data of the box you're looking at |
| `/shulkerstickers clear [icon\|name\|all]` | Admin | Clears the box you're looking at |
| `/shulkerstickers reload` | Admin | Reloads the config |

## 9. Edge cases

| Case | Behavior |
|---|---|
| Someone has the box open at Save | The color change is refused ("Box is in use"). The icon and name apply; displays stay hidden until the box closes |
| Two players try to edit the same box | The second gets "Being edited by ‹player›" |
| Box broken, moved or changed while the editor is open | Save is refused, the editor closes with a message, nothing is charged |
| Player walks out of reach or logs out with the editor open | The editor closes; the staged changes are discarded |
| Box has a vanilla `lock` | Editing requires holding the key, the same as opening the box |
| Player has container access but not build access in a claim | The editor is refused |
| Off-hand holds a totem or shield | Nothing happens unless "Use off-hand item" is clicked |
| Sneak + empty main hand + block in the off-hand | The editor opens instead of placing the block (§5.1); `/shulkerstickers toggle` restores vanilla |
| Observers, comparators, shulker loaders next to the box | Recoloring doesn't pulse observers; comparators are refreshed |
| Icon data made with creative mode or `/give` | Re-checked when drawn; invalid or oversized icons aren't drawn |
| Icon's item no longer exists (a removed mod, or a Minecraft change) | Nothing is drawn; the raw data is kept |
| Name contains a blocked word | Save is refused with a message |
| Adventure / spectator mode | Adventure denied unless allowed in the config; spectator always denied |
| Box facing down or sideways | The icon goes on whichever face was clicked |
| Box blocked from opening (needs half a block of clear space) | Displays have no hitbox, so they never block opening |
| Dark storage room | Displays take light from the space in front of the face; `icon.full_bright` is optional |
| Pistons, explosions | The box drops as an item; data kept |
| Paint your Shulkers! installed on a client | Both mods use the same gesture. Behavior to be confirmed in competitor tests; documented in the README |
| Chunk unloads and reloads | Displays re-attached (Fabric) or re-spawned (Paper) |
| A wall of 1,000+ decorated boxes | Kept in check by view ranges; performance test on both platforms |
| Canvas/Folia: a box on a region border | All work for a box runs on its region's thread; menus run on the player's thread |

## 10. Acceptance criteria

GT = automated test (Fabric GameTest or Paper integration test); M = checked manually. Unless marked, each one must pass on **Fabric, Paper and Canvas**.

1. **AC-1 Recolor (GT):** saving a color change between all 17 variants keeps all 27 slots, the name, the lock, the icon and `facing`, and drops no items.
2. **AC-2 No duplication during recolor (GT):** saving a recolor while a hopper is pulling from the box produces no duplicated or lost items.
3. **AC-3 Icon (GT):** using the off-hand item and saving leaves the off-hand stack unchanged; the stored icon holds only the allowed visual properties.
4. **AC-4 Persistence (GT):** every row of the §6.2 matrix passes.
5. **AC-5 Vanilla client (M):** an unmodified 26.3 client sees icons and names, uses the editor and the rename screen, and gets no disconnects or errors.
6. **AC-6 Uninstall (M):** with the mod or plugin removed, a world loads, decorated boxes open and break with their contents intact, and no leftover entities exist.
7. **AC-7 Config (GT/M):** each setting takes effect after `/shulkerstickers reload`.
8. **AC-8 Performance (M):** 1,000 decorated boxes loaded add less than 0.5 ms/tick.
9. **AC-9 No main-hand conflict (GT):** sneak + right-click on a box with any item in the main hand behaves exactly as in vanilla.
10. **AC-10 Protection (GT/M):** spawn protection, a locked box, and claims from WorldGuard, GriefPrevention and Common Protection API all refuse the editor and change nothing.
11. **AC-11 Staging (GT):** Cancel, Esc, disconnecting and walking out of reach all leave the box unchanged and charge nothing; Save applies all staged changes together.
12. **AC-12 Menu safety (GT):** every click type (shift-click, number keys, off-hand swap, double-click, drag, click outside) leaves the player's inventory and the box exactly as they were.
13. **AC-13 Portability (GT/M):** an item stickered on Fabric shows the same sticker on Paper, and the reverse. A 26.3 world upgraded to 26.4 keeps every icon.
14. **AC-14 Competitors (M):** no crashes, data loss or broken gestures with HerrShulker, Paint your Shulkers! and the shulker armor-trim mod installed alongside, wherever their platforms overlap with ours.
15. **AC-15 Text (M):** every menu string shows translated text, not raw keys, on a vanilla client; the first-placement message appears once and `/shulkerstickers help` repeats it.
16. **AC-16 Redstone (GT):** recoloring next to an observer produces no pulse, and a comparator reads the correct signal afterward.

## 11. Release plan

| Milestone | Scope | Exit criteria |
|---|---|---|
| **M0: spike** (about 1.5–2 weeks) | **First: confirm that Canvas (and Folia) have a usable 26.3 build.** Set up the three-module repo. On Fabric, Paper and Canvas: a display on a placed box, the recolor swap with a test, the anvil-style rename screen on a vanilla client, data surviving break and pick-up, and (Canvas) spawning displays and opening menus from the right threads. Check every item in §12's verify list | AC-1 on all three platforms; a go/no-go on the Paper/Canvas rendering approach |
| **0.1 MVP (alpha)** | **Fabric + Paper + Canvas**, all MVP stories. Modrinth (Fabric and Paper/Canvas jars) and Hangar (Paper/Canvas jar) alpha builds | AC-1 to AC-7, AC-9 and AC-11 to AC-16 on **all three platforms** |
| **0.2 Beta** | Lid animation, claims integrations, costs, performance pass | All acceptance criteria on all three platforms, including AC-8 and AC-10 |
| **1.0** | Port to 26.4; optional Fabric client companion (inventory overlay, client-drawn icons, inventory editing); README and listing copy | Release-candidate soak on test servers for each platform; zero data-loss bugs open |
| Ongoing | Each new Minecraft release: best effort within 2 weeks of the platform's stable build | |

Adding Paper and Canvas roughly **doubles the work up to 0.1**: shared `core` code helps, but the rendering, menus, threading and testing are built twice.

## 12. Risks, items to verify, decisions

### Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Two platforms double the testing and maintenance | Slower releases | Rules live only in `core`; one acceptance matrix for both; CI runs both test suites |
| Paper 26.3 stays in beta; Canvas may not have 26.3 yet | Paper/Canvas launch slips | 0.1 is gated on all three platforms. Confirm Canvas's 26.3 status in week 1 of M0. If one platform lacks a usable 26.3 build when the rest are ready, decide then whether to hold the release or ship that platform as a beta. Paper builds are labeled beta for as long as Paper's are |
| Folia/Canvas thread-safety bugs | Crashes or corrupted data on Canvas | Region/entity schedulers only; a dedicated Canvas test server before 0.2 |
| Hand-rolled rename screen misbehaves on vanilla clients | Rename feature glitchy | Prove it in M0; fallback: rename only at a real anvil for 0.1 |
| Menu duplication exploits | Fatal to trust | Reject every non-button interaction; AC-12 as a release gate |
| Items lost or duplicated during recolor | Fatal to trust | Single-tick swap on the owning thread; AC-1 and AC-2 as release gates |
| Icons fail after a Minecraft update | Icons vanish | Our own versioned format; AC-13 upgrade test |
| Polymer lags behind a new Fabric version | Fabric release blocked | Fallback to real entities that are never saved (as on Paper) |
| Gesture collision with Paint your Shulkers! | Confused players | Competitor tests; document it; `toggle` |
| Too many display entities in a big storage wall | Client frame rate drops | Short view ranges; AC-8 |

### Verify list (answered in the M0 spike, 11 Nov 2026)

All items below were checked against 26.3 source, tests or real-server runs; details are in `docs/spike/m0-fabric.md` and `docs/spike/m0-paper.md`. Short answers:

- **Fabric:** the event is `UseBlockCallback`; a server-side `AnvilMenu` with `ContainerLevelAccess.NULL` receives the rename text through `setItemName`; block entities keep `custom_data`; the shulker loot table drops it (fixed with `MODIFY_DROPS`); Polymer has a 26.3 build.
- **Paper:** the off-hand placement is stopped by also denying the OFF_HAND interact event; `MenuType.ANVIL` + `AnvilView#getRenameText()` exist; block persistent data does **not** reach the dropped item without our datapack; non-persistent entities are removed cleanly; datapack item tags are visible to plugins; MockBukkit supports 26.3 with gaps.
- **Canvas/Folia:** Canvas 26.3 is stable; region and entity schedulers work as documented; display entities spawn from region threads.
- **Vanilla:** anvil, dye recipe and cauldron keep `custom_data`; boxes can't go in bundles; the server can tell a box is open (the lid status lags a tick, so open menus are checked too); the rename limit is 50.

Still needs a vanilla client: the rename screen at zero XP cost, icon orientation on all six faces, and ghost items after rejected clicks.

### Original verify list

- **Fabric:** the use-block event's name and signature after the 26.1 renames; that a server-side anvil menu receives the rename text; that a block entity keeps `custom_data`; the shulker loot table's copy function; that Polymer's virtual-entity module has a 26.3 build.
- **Paper:** that `PlayerInteractEvent` lets us stop the off-hand placement; Paper's anvil view API for reading the rename text; that block-entity persistent data travels to the dropped item when a box is broken; that entities marked never-to-save are handled correctly on chunk unload; whether datapack item tags are available to plugins; MockBukkit support for 26.3.
- **Canvas/Folia:** that Canvas has a 26.3 build; the region and entity scheduler APIs; that display entities can be spawned from a region thread.
- **Vanilla:** the anvil, dye recipe and cauldron keep `custom_data`; shulker boxes can't go in bundles; the server can tell when a box is opening or open; the rename field's length limit.

### Decisions (11 Nov 2026)

- **Gesture:** off-hand item plus an empty main hand; main-hand use is never intercepted.
- **Dependencies:** Polymer is OK on Fabric; otherwise keep them minimal. No GUI or config libraries; nothing extra on Paper.
- **Name placement:** under the icon, on the same face.
- **Versions:** 26.3 and newer; best effort to support each new release quickly.
- **Editor:** stays open; Save applies; **Esc = Cancel**.
- **Rename:** free, with no anvil, name tag or XP.
- **License:** MIT (in the repo); Polymer's LGPL notice included.
- **Review items 1–8 and 10 accepted:** versioned minimal icon format, server-side translation, menu anti-duplication, recolor without observer pulses, re-checking icons when drawn, display lighting, lock and build-permission checks, name moderation, top/bottom rotation.
- **Help:** a chat message on first placement; `/shulkerstickers help`.
- **Competitors:** noted in §2; testing with them installed (AC-14).
- **Platforms:** Fabric + Paper + Canvas.
- **Naming:** Shulker Stickers / `shulker_stickers`; all commands are `/shulkerstickers <param>`.

### Proposed in v0.4 (awaiting the owner)

- **`face` key** for the exact icon face on a placed box (§6.1).
- **`icon_visual` as JSON** instead of SNBT (§6.1).
- **`name_hidden` key** for names saved without `name.display` permission (§6.1).
- **Name without an icon** gets the clicked face (§6.1).
- **Blocked-word syntax:** `/regex/` vs whole words (§8).
- **Paper drop path via a built-in datapack** that overrides the shulker loot tables (§6.2), with the conflict risk noted.
- **Locked boxes (§9):** vanilla wants the key in the main hand, but the gesture needs an empty main hand. Fabric currently accepts the key in **either** hand; Paper currently **always refuses** locked boxes. Proposed: accept the key in the off-hand on both.
- **Claims in 0.1 (§7.3, AC-10):** Paper honors claim plugins only through the right-click they already cancel. It doesn't fire fake block-break/place events, because logging plugins would record edits that never happened. So a claim that allows opening containers but not building still lets the editor open. Per-plugin build checks are planned for 0.2. Fabric uses spawn protection, `mayInteract` and Common Protection API.
- **Apply failure closes the editor:** §5.2 says a failed Save keeps the editor open with the reason, but §9 says a box that's gone or changed closes it. The builds follow §9 for those cases.

### Closed 11 Nov 2026 (no open questions)

- **Q9 Launch order:** Fabric, Paper and Canvas all ship at 0.1.
- **Q10 Java package / Maven group:** `io.github.jekitchen.shulkerstickers`.
