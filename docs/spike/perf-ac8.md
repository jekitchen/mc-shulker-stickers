# AC-8 groundwork: server cost of 1,000 decorated boxes

Measured 11 Nov 2026 on Windows 11, Java 25, one machine. Target (PRD AC-8): **< 0.5 ms/tick** with 1,000 decorated boxes loaded, on each platform.

**Result: Fabric is well under budget. Paper and Canvas are over it, by about 0.2 ms**, because they draw with real display entities. Two entities per box (icon + name) cost about 0.3 µs each per tick, and the plugin itself does no per-tick work.

## Method

The same scene on every platform is a 50 × 20 wall of 1,000 shulker boxes facing south. Each box has a cobblestone icon on the lid and a name, which makes two displays per box.

- **Fabric:** `./gradlew :fabric:runGameTest -Pperftest` runs `PerfGameTests.thousandDecoratedBoxes`, which is otherwise a no-op that passes at once. It waits 200 ticks so the other GameTests finish, records 100 server ticks, then builds the wall. A mock player stands 8 blocks in front of it so Polymer has someone to send the displays to. After 20 ticks it records 100 more ticks, then calls the mod's only per-tick hook (`StickerDisplays.updateOpenBoxes`) 200 times directly, timing each call. Tick durations come from `ServerTickEvents` start/end.
- **Paper / Folia / Canvas:** `./gradlew :paper:runServer -Pperftest`, or `:paper:runCanvas -Pperftest`, runs `PerfTest`, which is only active with that flag:
  - It records 200 ticks through `ServerTickEndEvent`; Canvas fires this event too.
  - It builds the wall on the region thread and spawns the displays, timing both.
  - It waits 200 ticks, then records 200 more.
  - It also logs the server's own report: `/mspt` on Paper, `/tps` on Folia and Canvas.
  - `-Pplain` builds the same wall with no stickers or names, which measures vanilla's own cost. `-Pnonames` draws icons only.
- **Comparison:** the "before" windows overlap server startup and are noisy (p99 up to 15 ms). So the cost is taken as the decorated "after" window minus the plain-box "after" window. Both have the same 1,000 ticking shulker block entities, so the difference is ours alone.

No real players were online. Real players add packet cost: the one-off spawn when they come into range, and tracking.

## Numbers

### Fabric (Polymer packet-only displays)

| Measure | Run 1 | Run 2 |
|---|---|---|
| Mod per-tick hook, 1,000 boxes: mean / p99 | 0.034 / 0.069 ms | 0.042 / 0.109 ms |
| Server tick without boxes, mean | 0.408 ms (noisy) | 0.189 ms |
| Server tick with 1,000 decorated boxes + 1 watcher, mean | 0.239 ms | 0.315 ms |
| Difference, including vanilla's cost for 1,000 shulker block entities | −0.17 ms (noise) | **+0.13 ms** |
| Building and drawing the wall (one-off) | 55 ms | 49 ms |

Polymer's displays aren't entities on the server, so they add no per-tick cost. The only per-tick work is the hide-while-open scan, and it stays under 0.11 ms at p99. **Under budget.**

### Paper 26.3 build 248 (real display entities)

| Scene ("after" window, 200 ticks) | Mean tick | p99 |
|---|---|---|
| 1,000 plain boxes (control), run 1 / run 2 | 0.432 / 0.403 ms | 0.84 / 0.73 ms |
| 1,000 boxes, icons only (1,000 displays) | 0.864 ms | 1.22 ms |
| 1,000 boxes, icons + names (2,000 displays), run 1 / run 2 / run 3 | 1.090 / 1.112 / 1.117 ms | 1.63 / 1.52 / 2.70 ms |

- **Our cost:** about **+0.44 ms** for icons only, and about **+0.68 ms** for icons and names. **Over budget.**
- **One-off costs:** placing and writing data took 54–72 ms. Spawning 2,000 displays took 74–98 ms, which is also what a chunk load costs. Redrawing all 1,000 took 41 ms.
- `setNoPhysics(true)` on the displays made no difference (1.117 ms), so it was not kept.

### Canvas 26.3 build 1010 (Folia fork)

| Scene | Mean tick (`ServerTickEndEvent`) | Region MSPT (`/tps`) |
|---|---|---|
| 1,000 plain boxes (control) | 0.398 ms | 0.72 ms |
| 1,000 boxes, icons + names | 1.056 ms | 1.59 ms |

- **Our cost:** about **+0.66 ms** by the tick-end event, or **+0.87 ms** by region MSPT. **Over budget**, the same as Paper.
- Folia uses the same entity code as Canvas, so it wasn't run separately.

## Update: Paper/Canvas displays only near players (built)

Paper and Canvas now spawn a box's icon only while a player is within `icon.view_range`, and its name only within `name.view_range`. Every loaded box with sticker data or a name is still tracked. Once a second, one task per chunk with tracked boxes runs on that chunk's region. It does one nearby-player lookup sized to cover the chunk's boxes, then spawns or removes displays when that changes what should be shown. Hide-while-open still applies.

Re-measured 11 Nov 2026 with the same wall ("after" window, 200 ticks). `-Pnearby` simulates a player within range of every box, which is the worst case: all 2,000 displays are needed.

| Scene | Paper 248: mean tick | Canvas 1010: mean tick | Plugin's proximity checks |
|---|---|---|---|
| 1,000 plain boxes (control) | 0.385–0.428 ms | 0.330 ms | 0.01 ms |
| 1,000 decorated boxes, **no player near** | 0.587–0.631 ms | 0.350 ms | 0.037–0.045 ms |
| 1,000 decorated boxes, untracked by the plugin (diagnostic, `-Pnotrack`) | 0.588 ms | — | — |
| 1,000 decorated boxes, **player near all of them** (simulated) | 1.135 ms | 1.092 ms | 0.041 ms |

- **No player near: our cost is about 0.03–0.04 ms/tick, under budget on both.** On Paper the wall still adds about 0.2 ms over plain boxes. The untracked run shows the same 0.2 ms, so it's the server's own cost for boxes that carry data (most likely Paper's chunk saving), not the plugin's. Canvas doesn't show it.
- **A player in view of all 1,000 boxes: still about 0.7 ms**, the cost of 2,000 display entities. That is a real 1,000-box wall in front of a player, so AC-8 isn't met for that case on Paper/Canvas. **Owner decision 6:** accepted for 0.1, with the `name.view_range` default lowered from 16 to 10, which brings the worst case to roughly 0.5 ms. The real fix is chosen in the 0.2 performance pass, after profiling with real players.
- **One-off costs:** spawning 2,000 displays took 90–96 ms, now when a player arrives rather than at chunk load.

## What would bring Paper/Canvas under budget

1. **Spawn displays only near players (recommended; now built, see above).** Draw a box's icon only while a player is within `icon.view_range` (32), and its name only within `name.view_range` (16).
   - Storage areas with no one nearby would cost nothing.
   - A player in front of the 50 × 20 wall would still see most icons but only about half the names. From the numbers above that's roughly 0.44 + 0.12 ≈ 0.56 ms, close to budget, and shorter ranges help further.
   - **Effort:** proximity checks have to run on the region thread for each player (Folia), with hysteresis to avoid churn.
2. **One entity per box,** for example names only on boxes with no icon. That's a design change for the PRD owner.
3. **Packet-only displays on Paper,** like Polymer. This needs server internals or a protocol library, which the PRD's "no dependencies" rule excludes.

## Still to check manually (spark + real clients)

- **Real players:** run spark (`/spark profiler`, `/spark tickmonitor`) on each platform with 1–5 real players standing in front of the 1,000-box wall. This adds the packet and tracking cost the measurements above leave out.
- **Client frame rate:** check it with 1,000 boxes (2,000 displays) in view, on a mid-range client. Lowering `view_range` should help, and the PRD lists this as a risk.
- **Joining near the wall:** the burst when a player joins or teleports next to the wall. Paper sends 2,000 entity spawns; Polymer sends the equivalent packets.
- **Chunk-load spikes** (74–98 ms for 1,000 boxes in one go here) in a real world, where the wall is spread over several chunks loading at different times.

## Displays appear up to a second late

Proximity is checked once a second per chunk, so a player flying in fast (elytra, riptide) can see boxes without icons for up to a second before they appear. Checking more often costs more; once a second is the 0.1 trade-off.

## Re-check after the 12 Nov changes

Re-measured 12 Nov 2026 after the display fixes (chunk-edge displays, lost-display check, the Fabric non-loading lookup), on the same machine:

- **Fabric:** the per-tick hook for 1,000 boxes averages 0.039 ms (p99 0.080 ms), the same as before.

**Re-measured 13 Nov 2026 (v0.2 lid motion, Fabric):**
- **First fix:** the displays' moves weren't reaching players. Letting every lid-face display tick with its chunk sent them, but cost about +0.2 ms per tick with 1,000 lid boxes.
- **Final version:** a lid's move is sent by the mod in the tick it happens, and nothing ticks otherwise. That's back to the old cost: the hook takes 0.034 ms, and the mean server tick is −0.03 ms against the run without boxes, which is noise.
- **Paper, player near all 1,000 boxes:** the plugin's proximity checks take 0.039 ms/tick and the mean tick is 1.12 ms, the same as before (0.041 ms and 1.135 ms). A first version of the lost-display check cost 0.065 ms/tick; it now only looks at displays standing in a neighbouring chunk.
