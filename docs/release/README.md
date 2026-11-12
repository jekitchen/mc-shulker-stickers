# Releasing

How to cut a release, and the store listing text. The PRD's release plan is `docs/PRD.md` §11.

## Before a release

1. **Exit criteria.** For 0.1: AC-1 to AC-7, AC-9 and AC-11 to AC-16 on Fabric, Paper and Canvas (`docs/PRD.md` §10). The automated part runs in CI. The manual part is the vanilla-client list in `docs/spike/M0-handoff.md` ("Checks that need a vanilla 26.3 client").
2. **CI is green on `main`**, including the `fabric-smoke` and `selftest` jobs.
3. **Version.** `mod_version` in `gradle.properties` is the version being released. Jars and metadata add `+<minecraft_version>`, for example `0.1.0-alpha.1+26.3`.
4. **Changelog.** `CHANGELOG.md` has a `## <version>` section. Replace "(not yet released)" with the date.

## Cutting it

```sh
git tag v0.1.0-alpha.1
git push origin v0.1.0-alpha.1
```

The `Release` workflow (`.github/workflows/release.yml`) does the following:
- checks that the tag is `v` + `mod_version`;
- runs the full build, the Fabric smoke test and the Paper, Folia and Canvas self-tests;
- creates a **draft** GitHub release with both jars and the changelog section as notes. Versions with a `-` (alpha, beta, rc) are marked as pre-releases.

Review the draft, then publish it on GitHub.

## Uploading to Modrinth and Hangar (manual)

Both stores need the owner's account. Upload the same jars as the GitHub release.

| Store | Jar | Loaders / platforms | Game versions | Channel |
|---|---|---|---|---|
| Modrinth | `shulker-stickers-fabric-…jar` | Fabric (Quilt untested) | 26.3 | Alpha |
| Modrinth | `shulker-stickers-paper-…jar` | Paper, Folia (other Paper forks untested) | 26.3 | Alpha |
| Hangar | `shulker-stickers-paper-…jar` | Paper | 26.3 | Alpha |

Modrinth dependencies:
- **Fabric jar:** Fabric API (required). Polymer is bundled, so don't list it.
- **Paper jar:** none. GriefPrevention, WorldGuard, Lands and Towny are optional ("Optional" if the store asks).

Canvas isn't a Modrinth or Hangar loader tag. Mention it in the description instead (done below).

## Listing text (draft)

**Name:** Shulker Stickers
**Summary (one line):** Recolor, label and rename placed shulker boxes. Server-side; works with vanilla clients.
**Categories:** Storage, Decoration, Utility. Environment: server-side only (clients need nothing).

**Description:**

> Find the right shulker box at a glance. Sneak and right-click a placed box with an empty main hand to open the Sticker editor:
>
> - **Recolor** it to any of the 16 dye colors, or undyed. No dye needed, and everything inside stays put.
> - **Stick an icon** on it: the item in your off-hand shows on the lid or on the side you clicked. You keep the item.
> - **Rename** it, and the name shows on the box under the icon.
>
> Save applies everything at once; Cancel or Esc changes nothing.
>
> **Works with vanilla clients.** Everything happens on the server, so players don't install anything.
>
> **Stickers stay with the box** when it's broken and picked up, pushed by a piston, blown up or placed by a dispenser. They also stay when the box moves between a Fabric and a Paper server, or when a whole world moves from one to the other.
>
> **Plays nicely with your server:**
> - Locked boxes need their key.
> - Spawn protection and claims are respected: Common Protection API mods on Fabric; GriefPrevention, WorldGuard, Lands and Towny on Paper.
> - Permissions work with LuckPerms.
> - Costs (XP levels or an item) are optional.
> - A word filter is available for names.
>
> **Safe to remove:** uninstall it and boxes keep their contents and color. The icon and name displays are never saved, so nothing is left behind.
>
> **Platforms:** Fabric (needs Fabric API) and Paper, including Folia and Canvas. Minecraft 26.3.
>
> This is an **alpha**: please report problems at https://github.com/jekitchen/mc-shulker-stickers/issues.

**Icon:** `docs/images/icon-512.png` (redraw with `docs/images/make_icon.py`).

**Gallery:** needs screenshots from a vanilla client: the editor, a wall of decorated boxes, and an icon on a side face.

**License:** MIT. Source: https://github.com/jekitchen/mc-shulker-stickers
