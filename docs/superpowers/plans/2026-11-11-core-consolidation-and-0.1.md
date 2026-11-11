# Core consolidation and 0.1 MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move every platform-free rule into `core`, then build the remaining 0.1 MVP features on Fabric and Paper/Canvas on top of it.

**Architecture:** `core` owns the data format, face rules, editor layout, config schema, translations, name and icon rules, costs and permission names. `fabric` and `paper` only adapt these to their APIs (events, menus, rendering, schedulers). Shared behavior is tested once in `core` with JUnit; platform behavior is tested with Fabric GameTests and MockBukkit plus real-server smoke runs.

**Tech Stack:** Java 25, Gradle 9.9.1, JUnit 6.1.3, Gson (compileOnly in core; both servers ship it), Fabric Loom 1.19.4 / Fabric API 0.166.0+26.3 / Polymer 0.18.7+26.3, paper-api 26.3.build.248-stable, MockBukkit 4.120.0.

**Spec:** `docs/PRD.md` (v0.3), plus the spike findings in `docs/spike/m0-fabric.md` and `docs/spike/m0-paper.md`.

## Global Constraints

- Mod/plugin ID `shulker_stickers`; plugin name `ShulkerStickers`; package `io.github.jekitchen.shulkerstickers`; command root `/shulkerstickers`.
- Minecraft 26.3+; Java 25.
- Dependencies: Fabric API + Polymer only on Fabric; nothing beyond the server API on Paper/Canvas. `core` uses only the JDK (+ Gson, provided by both servers).
- Data lives in `minecraft:custom_data → PublicBukkitValues`, keys `shulker_stickers:<name>`; identical on both platforms.
- Nothing saved to the world except box data (no saved entities).
- Folia/Canvas: world and entity work on the region scheduler; player work on the entity scheduler; never the global main thread.
- Every player-visible string translated on the server; English ships first.
- Commits use the GitHub noreply email only.

## Decisions taken without the owner (to confirm in the morning)

1. **Side anchor:** a new key `shulker_stickers:face` (lowercase face name) records the exact face of the icon on a placed box. `rot` keeps meaning rotation (0–3) on top/bottom faces. On placement, `face` is recomputed from the anchor rule.
2. **Name without an icon:** whenever Save writes a name, it also writes `anchor`/`face` for the face that was clicked, so the name has a face. Old data with a name but no anchor draws on the lid.
3. **Blocked words:** entries wrapped in slashes (`/regex/`) are regular expressions; anything else is a case-insensitive whole-word match.
4. **Editor layout:** Paper's slots win (preview 18, use off-hand 20, remove icon 21, rename 22, cancel 25, save 26; colors 0–16).

## Review Focus

1. Sticker data from another version or hand-edited data (wrong types, unknown `v`, missing keys) must not crash and must not be erased on read — `StickerCodecTest` covers wrong types and unknown versions.
2. Config files with missing keys, wrong types or broken JSON must load defaults and report warnings, never crash the server — `ConfigLoaderTest`.
3. Names at the 50-character limit, empty after trimming, or with emoji must truncate on code points, not chars — `NameRulesTest#truncatesOnCodePoints`.
4. A blocked-word regex that is invalid must be reported and skipped, not throw at Save — `NameRulesTest#invalidRegexIsSkipped`.
5. Translations missing a key in a non-English language fall back to English, and a key missing everywhere shows the key, not a crash — `TranslationsTest`.

---

### Task 1: Sticker data format in core

**Files:**
- Create: `core/src/main/java/io/github/jekitchen/shulkerstickers/core/data/StickerFormat.java`, `.../core/data/Sticker.java`, `.../core/data/StickerCodec.java`
- Test: `core/src/test/java/io/github/jekitchen/shulkerstickers/core/data/StickerCodecTest.java`

**Interfaces:**
- Produces: `StickerFormat.NAMESPACE = "shulker_stickers"`, `StickerFormat.VERSION = 1`, short key names `V="v"`, `ICON_ID="icon_id"`, `ICON_VISUAL="icon_visual"`, `ANCHOR="anchor"`, `ROT="rot"`, `FACE="face"`, `List<String> KEYS`, `String fullKey(String shortKey)` → `"shulker_stickers:" + shortKey`.
- Produces: `record Sticker(Optional<String> iconId, Optional<String> iconVisual, Anchor anchor, int rot, Optional<Face> face)` with `boolean hasIcon()`.
- Produces: `StickerCodec.encode(Sticker) -> Map<String,Object>` (short keys; values `Integer` or `String`; absent optionals omitted; always writes `v`), `StickerCodec.decode(Map<String,?>) -> Optional<Sticker>` (empty if `v` missing, not an int, or > VERSION; wrong-typed fields are ignored; unknown anchor → empty).

- [ ] Tests: round trip; wrong types ignored; `v=2` → empty; missing anchor → empty; rot normalized with floorMod 4.
- [ ] Implement, run `./gradlew :core:test`, commit.

### Task 2: Face rules in core

**Files:** Create `core/.../core/Faces.java`; test `FacesTest.java`.

**Interfaces:**
- `List<Face> Faces.sides(Face facing)` — the four faces perpendicular to `facing`, in `Face` declaration order.
- `Face Faces.iconFace(Anchor anchor, Face facing, Optional<Face> storedFace)` — LID→facing, BASE→opposite, SIDE→storedFace if it is a side, else `sides(facing).get(0)`.
- `Face Faces.sideToward(Face facing, double dx, double dy, double dz)` — the side whose normal has the largest dot product with the vector.
- `boolean Faces.isSide(Face facing, Face face)`; `int[] Face.normal()` added to `Face`.

### Task 3: Editor layout in core

**Files:** Create `core/.../core/editor/EditorLayout.java` (moved from Paper, platform-free); test.

**Interfaces:** constants `SIZE=27, PREVIEW=18, USE_OFFHAND=20, REMOVE_ICON=21, RENAME=22, CANCEL=25, SAVE=26`; `sealed interface Button` (Color(BoxColor), Preview, UseOffhand, RemoveIcon, Rename, Cancel, Save); `Optional<Button> buttonAt(int slot)`; `boolean isMenuSlot(int rawSlot)`.

### Task 4: Config schema and loader in core

**Files:** Create `core/.../core/config/StickerConfig.java`, `Cost.java`, `ConfigLoader.java`; test `ConfigLoaderTest.java`. `core/build.gradle.kts`: `compileOnly`+`testImplementation` Gson 2.15.0.

**Interfaces:**
- `record Cost(Type type, int amount, String itemId)`, `enum Type { NONE, XP_LEVELS, ITEM }`, `Cost.NONE`.
- `record StickerConfig(boolean colorEnabled, boolean iconEnabled, boolean nameEditEnabled, boolean nameDisplayEnabled, Cost colorCost, Cost iconCost, Cost nameCost, List<String> iconBlocklist, int iconMaxDataBytes, int iconViewRange, int nameViewRange, boolean iconFullBright, int nameMaxDisplayChars, List<String> nameBlockedWords, boolean hideWhileOpen, boolean firstPlaceMessage, boolean allowAdventureMode)`; `StickerConfig.DEFAULTS` with the PRD §8 defaults (true…, NONE, [], 2048, 32, 16, false, 24, [], true, true, false).
- JSON shape is nested by the dotted PRD key: `{"color":{"enabled":true,"cost":{"type":"none"}},"icon":{...,"blocklist":[],"max_data_bytes":2048,"view_range":32,"full_bright":false},"name":{"edit_enabled":true,"display_enabled":true,"cost":{...},"view_range":16,"max_display_chars":24,"blocked_words":[]},"display":{"hide_while_open":true},"help":{"first_place_message":true},"allow_adventure_mode":false}`.
- `ConfigLoader.Result(StickerConfig config, List<String> warnings)`; `ConfigLoader.parse(String json) -> Result`; `ConfigLoader.loadOrCreate(Path file) -> Result` (writes the defaults file if missing); `ConfigLoader.toJson(StickerConfig) -> String`.

### Task 5: Translations in core

**Files:** Create `core/.../core/text/Translations.java`, `core/src/main/resources/assets/shulker_stickers/lang/en_us.json`; test.

**Interfaces:** `Translations.load()` reads bundled `lang/*.json` by known locale list; `String translate(String locale, String key, Object... args)` — `{0}`-style placeholders via `MessageFormat`-free replacement; locale normalized (`en_US`→`en_us`); falls back to `en_us`, then to the key. `Set<String> keys()`. `TextKeys` constants for every key below.

Keys: `editor.title`, `editor.preview`, `editor.use_offhand`, `editor.use_offhand.empty`, `editor.use_offhand.blocked`, `editor.remove_icon`, `editor.rename`, `editor.cancel`, `editor.save`, `editor.save.cost`, `editor.color.<dye>` (17), `editor.discarded`, `rename.title`, `deny.permission`, `deny.protected`, `deny.locked`, `deny.in_use_editor`, `deny.adventure`, `save.box_in_use`, `save.box_gone`, `save.blocked_word`, `save.cannot_pay`, `help.first_place`, `help.text`, `toggle.off`, `toggle.on`, `inspect.*`, `clear.done`, `reload.done`, `reload.warnings`.

### Task 6: Name and icon rules in core

**Files:** Create `core/.../core/rules/NameRules.java`, `IconRules.java`; tests.

**Interfaces:**
- `NameRules.MAX_LENGTH = 50`; `String displayText(String name, int maxChars)` (truncate on code points, append `…`); `BlockedWords BlockedWords.compile(List<String> entries)` with `Optional<String> firstMatch(String name)` and `List<String> warnings()`.
- `IconRules.VISUAL_COMPONENTS` = `minecraft:custom_model_data, minecraft:item_model, minecraft:enchantment_glint_override, minecraft:dyed_color, minecraft:trim, minecraft:profile, minecraft:banner_patterns, minecraft:base_color, minecraft:potion_contents, minecraft:firework_explosion, minecraft:fireworks`; `boolean isBlocked(String itemId, Collection<String> blocklistIds)`; `boolean fitsSize(Sticker, int maxBytes)` (UTF-8 bytes of iconId + iconVisual).

### Task 7: Costs and permissions in core

**Files:** `core/.../core/rules/Costs.java`, `core/.../core/Permissions.java`; tests.

**Interfaces:** `List<Cost> Costs.forChanges(Changes, StickerConfig)` (only changed parts, NONE skipped); `Permissions.COLOR="shulker_stickers.color"`, `ICON`, `NAME`, `NAME_DISPLAY="shulker_stickers.name.display"`, `ADMIN="shulker_stickers.admin"`.

### Task 8: Platforms switch to core (Fabric, then Paper)

Replace the platform copies (`fabric/.../data/StickerData` keys, `editor/DisplayFaces`; `paper/.../StickerKeys`, `Faces`, `EditorLayout`) with the core classes. Keep every existing GameTest and MockBukkit test green. Add a cross-platform test: the encoded map from `StickerCodec` matches what each platform writes.

### Task 9 (parallel, Fabric agent): 0.1 features on Fabric

Hide displays while open; one editor per box (lock) with "Being edited by"; permissions (vanilla op levels; Fabric Permissions API only if present — it is not a dependency); Common Protection API only if present; vanilla `lock` key check; adventure/spectator rules; reach/logout closes the editor; costs at Save; translated strings via core; icon visual properties + blocklist (ids + `#shulker_stickers:icon_blocklist` tag) + size cap, re-checked when drawn; name display with blocked words and `name.display` permission; first-run help (player attachment); `/shulkerstickers help|toggle|inspect|clear|reload`; config file `config/shulker_stickers.json`. GameTests for each.

### Task 10 (parallel, Paper agent): 0.1 features on Paper/Canvas

Same list on Paper, with `plugins/ShulkerStickers/config.json`, Bukkit permissions, player PDC for first-run help, Brigadier commands via `LifecycleEvents.COMMANDS`; all on region/entity schedulers. MockBukkit tests where possible; smoke runs on Paper, Folia and Canvas.

### Task 11: CI and README

GitHub Actions workflow (`.github/workflows/build.yml`): Java 25 Temurin, `./gradlew build` (core tests, Fabric GameTests, Paper tests) on push and PR. README with what it does, install, gesture, commands, config, building.
