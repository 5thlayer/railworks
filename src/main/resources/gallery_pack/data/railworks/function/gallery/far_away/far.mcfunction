# The far-away scene's far part, run at its marker. What an earlier build left out there goes first,
# broken as clear breaks a plot, so its Spans are cut, its Station leaves the level's Stations and its
# hatch takes its record; then the way is cleared down to the line's foot, and the Pylons stood every
# 30 blocks on to Depot at its east end. The chunks are let go of a second on (far_away/sweep).
setblock ~258 ~2 ~-7 minecraft:air destroy
setblock ~258 ~1 ~-7 minecraft:air destroy
setblock ~258 ~ ~-7 minecraft:air destroy
setblock ~260 ~2 ~-5 minecraft:air destroy
fill ~51 ~ ~-6 ~261 ~1 ~-6 minecraft:air destroy
fill ~22 ~ ~-8 ~262 ~6 ~-4 minecraft:air
function railworks:gallery/column {x:51,z:-6,top:1}
function railworks:gallery/column {x:81,z:-6,top:1}
function railworks:gallery/column {x:111,z:-6,top:1}
function railworks:gallery/column {x:141,z:-6,top:1}
function railworks:gallery/column {x:171,z:-6,top:1}
function railworks:gallery/column {x:201,z:-6,top:1}
function railworks:gallery/column {x:231,z:-6,top:1}
function railworks:gallery/column {x:261,z:-6,top:1}
railworks span ~21 ~ ~-6 ~51 ~ ~-6
railworks span ~51 ~ ~-6 ~81 ~ ~-6
railworks span ~81 ~ ~-6 ~111 ~ ~-6
railworks span ~111 ~ ~-6 ~141 ~ ~-6
railworks span ~141 ~ ~-6 ~171 ~ ~-6
railworks span ~171 ~ ~-6 ~201 ~ ~-6
railworks span ~201 ~ ~-6 ~231 ~ ~-6
railworks span ~231 ~ ~-6 ~261 ~ ~-6
fill ~260 ~ ~-5 ~260 ~1 ~-5 minecraft:stone_bricks
setblock ~260 ~2 ~-5 railworks:station{name:"Depot"}
setblock ~258 ~ ~-7 minecraft:chest[facing=south]
setblock ~258 ~1 ~-7 minecraft:hopper[facing=down]
setblock ~258 ~2 ~-7 railworks:hatch[mode=requester]
railworks hatch ~258 ~2 ~-7 requester minecraft:iron_ore 2
tag @s add railworks.gallery.far_away.built
schedule function railworks:gallery/far_away/sweep 20t
