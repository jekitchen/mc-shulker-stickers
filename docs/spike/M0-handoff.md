# M0 spike and 0.1 build-out: handoff (11 Nov 2026)

**Result:** go on all three platforms. Fabric, Paper and Canvas (and Folia) run the same features from one shared `core`, and every automated check passes. What's left needs a real game client or a decision from the owner.

Branch: `spike/m0` (not pushed). Worktree: `.claude/worktrees/m0-spike`.

## Where things stand

| | Fabric | Paper / Folia / Canvas |
|---|---|---|
| Automated tests | 82 GameTests (incl. Fabric Permissions API and Common Protection API run in tests) | 98 unit tests (MockBukkit) + 23-check real-server self-test on Paper 248, Folia 6 and Canvas 1010 |
| Shared `core` | 97 unit tests | (same) |
| Editor, recolor, icon, rename, Save/Cancel | Done | Done |
| Data survives break / pistons / explosions / dispensers | Done | Done (built-in datapack) |
| Config, translations, permissions, costs, locking, hide-while-open, first-run tip, commands | Done | Done |
| Items move between Fabric and Paper | Identical bytes (checked by a parity review) | (same) |
| AC-8 (1,000 boxes < 0.5 ms/tick) | 0.04 ms | Under budget with no player near; **~0.7 ms with a player in view of all 1,000** |

`./gradlew build` runs all of it except the Paper self-test (`./gradlew :paper:runServer -Pselftest`, `:paper:runFolia -Pselftest`, `:paper:runCanvas -Pselftest`).

Each platform had an independent code review, then a cross-platform parity review, then a review of the fixes (which caught a free-edit hole in Paper payments, now fixed: payment is taken at Save and unapplied parts are refunded). Every critical and important finding was fixed with a test that failed first.

## Owner decisions (11 Nov 2026) and the work they create

All eleven v0.4 proposals are decided; see `docs/PRD.md` §12. Code changes still to make:

1. **Locked boxes (Paper):** accept the key in either hand, as Fabric does.
2. **Claims (Paper):** soft integrations for GriefPrevention, WorldGuard, Lands and Towny; `protection.simulate_place_event` (default off) for other claim plugins.
3. **World migration (both):** on chunk load, move sticker data stored in the other platform's spot into ours.
4. **Drop copy (Fabric):** copy only our keys. **(Paper):** warn at startup if another datapack overrides the shulker loot tables.
5. **AC-8:** `name.view_range` default 16 -> 10 on both.
6. **`icon.enabled = false` (Fabric):** hide existing icons.
7. **Cosmetics:** move icon/name scale and offsets, editor filler and unavailable-button items, and the `inspect` layout into `core`; start from Fabric's values.

Already matching the decisions: the data format, name-only face, blocked-word syntax and failed-Save behavior.

## Checks that need a vanilla 26.3 client

Start a server with `./gradlew :fabric:runServer`, `:paper:runServer` or `:paper:runCanvas` and join with an unmodded 26.3 client. Hold cobblestone in the off-hand.

1. **Gesture:** sneak + right-click a placed box with an empty main hand opens "Edit: …". With a block in the off-hand, no block is placed and no ghost block shows.
2. **Menu safety:** try shift-click, number keys, F, double-click, drag, Q and clicking outside the window. Nothing leaves the editor and no ghost items appear.
3. **Rename screen:** type a name and click the result slot at 0 XP. It must work at repair cost 0, the paper must never reach the inventory, and Esc must return to a working editor.
4. **Icons:** on all six faces, the icon faces outward and isn't mirrored. On top and bottom, the image's top points the way you were facing. Both builds assume the display model's front faces +Z; if every icon looks backwards, that one assumption is wrong.
5. **Icon looks:** a dyed leather helmet, an enchanted sword (glint), a player head and a banner should each look like the item.
6. **Names:** readable under the icon, and centered on the face for a name-only box.
7. **Hide while open:** the displays disappear while the box is open and come back after it closes.
8. **Costs, permissions, commands, first-run tip, dispenser, walking out of reach:** see the lists in `docs/spike/fabric-0.1-notes.md` and `docs/spike/paper-0.1-notes.md`.
9. **Optional Fabric integrations with real mods:** LuckPerms and a Common Protection API claim mod (e.g. Flan). Both APIs are exercised in GameTests with fake providers, but not yet with the real mods.

## Files

- Spec: `docs/PRD.md` (v0.4)
- Plan: `docs/superpowers/plans/2026-11-11-core-consolidation-and-0.1.md`
- Findings: `docs/spike/m0-fabric.md`, `docs/spike/m0-paper.md`, `docs/spike/fabric-0.1-notes.md`, `docs/spike/paper-0.1-notes.md`, `docs/spike/perf-ac8.md`
