# 0.2: which further claim plugins to support on Paper (for the owner to decide)

The PRD's 0.2 row says "further claims integrations". 0.1 already asks GriefPrevention, WorldGuard, Lands and Towny directly. It also has an opt-in test block-place event for every other claim plugin (`protection.simulate_place_event`). Lock plugins that cancel the right-click, such as Bolt, are respected already: the gesture skips a click another plugin denied, and `EditorFlowTest#aProtectionPluginDenialIsHonored` covers that.

Checked 12 Nov 2026 (Modrinth, Hangar, GitHub releases):

| Plugin | Kind of server | 26.x status | API | Recommendation |
|---|---|---|---|---|
| **BentoBox** (and its add-ons: BSkyBlock, AcidIsland, AOneBlock…) | Skyblock / one-block | Release 3.23.7 on 11 Nov 2026; Modrinth lists 26.3 | Public Maven API, EPL-2.0. Island protection flags (`PLACE_BLOCKS`, `BREAK_BLOCKS`) | **Add.** Skyblock players use shulker boxes heavily, and island rules are exactly "build access" |
| **PlotSquared** | Creative / plot servers | 7.6.0, Aug 2026 | Public API (IntellectualSites Maven) | **Add if plot servers are a target.** There, "can build on this plot" is the right check |
| **Residence** | Survival | 6.0.2.3, Jul 2026 | API in the jar (Zrips) | **Maybe.** Popular in some regions; check its 26.3 build and API access first |
| **HuskClaims** | Survival | Last release Jul 2025 (1.21.7) | Public API | **Wait.** No 26.x build yet |
| **Factions** forks (FactionsUUID, SaberFactions…) | Factions | Many forks, mostly on SpigotMC | Each fork differs | **Skip.** The test place event covers them |
| **GriefDefender** | Survival | Paid | Public API | **Skip unless asked.** We can't test it without a licence |
| **Bolt / other lock plugins** | Any | Bolt 26.3 on Modrinth | — | **Nothing to add.** They cancel the click, which we respect. A `runClaims` run with Bolt installed could confirm it |

**Suggested scope for 0.2:** BentoBox, plus PlotSquared if plot servers matter. Each is one small `ClaimCheck` class (see `paper/.../claims/`), a compile-only API dependency, and a `runClaims` self-test probe with the real plugin downloaded.

**What it doesn't need:** Fabric. Its claim mods mostly support Common Protection API, which is already wired in.
