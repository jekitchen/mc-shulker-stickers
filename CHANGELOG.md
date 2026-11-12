# Changelog

Each release has a Fabric jar and a Paper jar (the Paper jar also runs on Folia and Canvas). Versions read `<version>+<Minecraft version>`.

## Unreleased (planned for 0.2)

- **Icons move with the lid:** when a box opens, the icon and name on its lid rise and turn with the lid, the same way vanilla moves the lid, instead of disappearing. Icons on the sides are still hidden while the box is open. Spectators opening a box don't move the lid in vanilla, so they don't move the icons either.
  - Known limitation (Paper): when another plugin opens a lid through the API (`Lidded#open()`) rather than a player opening the box, there's no event to follow, so the icons stay where they are.

## 0.1.0-alpha.1 (not yet released)

First alpha, for Minecraft 26.3. Server-side: players need no mod.

- **Sticker editor:** sneak and right-click a placed shulker box with an empty main hand. Recolor it to any of the 16 dye colors or undyed, stick the item in your off-hand on it as an icon (you keep the item), or rename it. Save applies everything at once; Cancel or Esc discards.
- **Icons and names on boxes:** the icon shows on the lid or the side you clicked, the name under it. Players see names within 10 blocks by default (`name.view_range`).
- **Stickers stay with the box:** breaking, pistons, explosions and dispensers keep them, and so does moving a box between Fabric and Paper servers, or moving a whole world from one to the other.
- **Protection:** locked boxes need the key in either hand. Spawn protection is respected. Claims are checked on Fabric through Common Protection API, and on Paper with GriefPrevention, WorldGuard, Lands and Towny. Other Paper claim plugins can be covered with `protection.simulate_place_event`, which is off by default.
- **Config, permissions and commands:** `/shulkerstickers help | toggle | inspect | clear | reload`, LuckPerms-compatible permission nodes, optional XP or item costs, and a word filter for names. On Paper, `reload` also draws boxes made by `/setblock`, `/clone`, structures or WorldEdit (Fabric draws them right away).
- **Uninstall-safe:** remove the mod or plugin and boxes keep their contents and color; icon and name displays aren't saved, so nothing is left behind.

Known limitations:

- Alpha: the gestures, editor and displays have been tested on real servers but not yet with a wide range of clients.
- Paper: with a player in view of 1,000 decorated boxes at once, displays cost about 0.7 ms/tick. The fix comes in the 0.2 performance pass.
- Fabric: Minecraft 26.3.x only. 26.4 support waits for a 26.4 build of Polymer.
- A box broken while the mod or plugin is uninstalled drops with its contents, name and color, but without its sticker (vanilla doesn't copy it). Boxes left in place keep their stickers, which come back on reinstall.
