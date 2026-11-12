# Shulker Stickers showcase: builds decorated boxes around you for the vanilla-client checks in
# docs/spike/M0-handoff.md. Run it as an operator, standing on flat ground with space around you:
#   /function shulker_stickers_showcase:build
# Boxes float 2 blocks up so you can look at their bottoms. Rows run east (+X) from where you stand.
# The stickers use Paper's storage layout, which Fabric reads too.

# Row 1, 3 blocks south: one box per facing, icon (diamond) on the lid, named after the facing.
setblock ~0 ~2 ~3 minecraft:shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:diamond","shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"Up"}
setblock ~2 ~2 ~3 minecraft:shulker_box[facing=down]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:diamond","shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"Down"}
setblock ~4 ~2 ~3 minecraft:shulker_box[facing=north]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:diamond","shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"North"}
setblock ~6 ~2 ~3 minecraft:shulker_box[facing=south]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:diamond","shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"South"}
setblock ~8 ~2 ~3 minecraft:shulker_box[facing=east]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:diamond","shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"East"}
setblock ~10 ~2 ~3 minecraft:shulker_box[facing=west]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:diamond","shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"West"}

# Row 2, 6 blocks south: boxes facing up with the icon (an arrow, so you can see which way it points)
# on each side face, then on the lid turned each of the four ways.
setblock ~0 ~2 ~6 minecraft:red_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:arrow","shulker_stickers:anchor":"side","shulker_stickers:rot":0,"shulker_stickers:face":"north"},CustomName:"Side north"}
setblock ~2 ~2 ~6 minecraft:red_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:arrow","shulker_stickers:anchor":"side","shulker_stickers:rot":0,"shulker_stickers:face":"south"},CustomName:"Side south"}
setblock ~4 ~2 ~6 minecraft:red_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:arrow","shulker_stickers:anchor":"side","shulker_stickers:rot":0,"shulker_stickers:face":"east"},CustomName:"Side east"}
setblock ~6 ~2 ~6 minecraft:red_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:arrow","shulker_stickers:anchor":"side","shulker_stickers:rot":0,"shulker_stickers:face":"west"},CustomName:"Side west"}
setblock ~8 ~2 ~6 minecraft:blue_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:arrow","shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"Lid rot 0 (south)"}
setblock ~10 ~2 ~6 minecraft:blue_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:arrow","shulker_stickers:anchor":"lid","shulker_stickers:rot":1},CustomName:"Lid rot 1 (west)"}
setblock ~12 ~2 ~6 minecraft:blue_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:arrow","shulker_stickers:anchor":"lid","shulker_stickers:rot":2},CustomName:"Lid rot 2 (north)"}
setblock ~14 ~2 ~6 minecraft:blue_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:arrow","shulker_stickers:anchor":"lid","shulker_stickers:rot":3},CustomName:"Lid rot 3 (east)"}

# Row 3, 9 blocks south: icon looks (dyed armor, enchantment glint, a head, a banner) and names.
setblock ~0 ~2 ~9 minecraft:white_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:leather_helmet","shulker_stickers:icon_visual":'{"minecraft:dyed_color":16711680}',"shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"Red helmet"}
setblock ~2 ~2 ~9 minecraft:white_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:diamond_sword","shulker_stickers:icon_visual":'{"minecraft:enchantment_glint_override":true}',"shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"Glint"}
setblock ~4 ~2 ~9 minecraft:white_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:player_head","shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"Head"}
setblock ~6 ~2 ~9 minecraft:white_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:white_banner","shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"Banner"}
setblock ~8 ~2 ~9 minecraft:white_shulker_box[facing=up]{CustomName:"A name and no icon"}
setblock ~10 ~2 ~9 minecraft:white_shulker_box[facing=up]{CustomName:"A very long box name that is far over the limit"}
setblock ~12 ~2 ~9 minecraft:white_shulker_box[facing=up]{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:emerald","shulker_stickers:anchor":"lid","shulker_stickers:rot":0,"shulker_stickers:name_hidden":1},CustomName:"Hidden name"}

# Row 4, 12 blocks south: a box locked with a stick (the key is in the chest next to it) and a
# plain box to decorate yourself with the editor.
setblock ~0 ~2 ~12 minecraft:black_shulker_box[facing=up]{lock:{items:"minecraft:stick"},PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:tripwire_hook","shulker_stickers:anchor":"lid","shulker_stickers:rot":0},CustomName:"Locked (key: stick)"}
setblock ~2 ~1 ~12 minecraft:chest{Items:[{Slot:0b,id:"minecraft:stick",count:1}]}
setblock ~4 ~1 ~12 minecraft:shulker_box[facing=up]

# A few things to stick on boxes, and a reminder.
give @s minecraft:cobblestone 16
give @s minecraft:leather_helmet
give @s minecraft:dirt 16
tellraw @s [{"text":"Shulker Stickers showcase built. ","color":"aqua"},{"text":"Row 1: every facing. Row 2: side faces and lid rotation. Row 3: icon looks and names. Row 4: a locked box (key in the chest) and a plain box. On Paper, run /shulkerstickers reload to draw boxes made by commands.","color":"gray"}]
