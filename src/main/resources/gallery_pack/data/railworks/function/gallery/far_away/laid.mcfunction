# Run at a far-away scene's marker once its old line is cut: the way is cleared down to the line's
# foot, the Pylons stood every 30 blocks on to Mine at its east end, and its provider, beside Mine's
# first car spot, filled. The chunks are let go of a second on (far_away/sweep).
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
fill ~260 ~ ~-4 ~260 ~2 ~-4 minecraft:stone_bricks
setblock ~260 ~3 ~-4 railworks:station{name:"Mine"}
fill ~253 ~ ~-8 ~253 ~2 ~-8 minecraft:stone_bricks
setblock ~253 ~3 ~-8 railworks:hatch[mode=provider]
railworks hatch ~253 ~3 ~-8 provider minecraft:iron_ore 8
tag @s add railworks.gallery.far_away.built
schedule function railworks:gallery/far_away/sweep 20t
