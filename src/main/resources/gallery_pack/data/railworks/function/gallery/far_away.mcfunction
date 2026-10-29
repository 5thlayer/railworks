# Trains far away: a line from the plot running on east for 240 blocks, past the chunks a player
# standing at the sign has loaded, to Mine, whose provider holds eight stacks of iron ore. By the
# plot, beside Smelter's first car spot, a requester asks for two, and a hopper under it empties it
# into a chest, so it keeps asking. Stations and hatches stand on pillars, in the row two blocks off
# the Rail at a Cargo car's chests' height. The automatic Train runs out to the Mine with no chunk
# of its way loaded, and comes back with the ore, again and again until the Mine runs dry. Commands
# build only in loaded chunks, so the line's chunks are force-loaded, the far part is built once
# they have loaded (far_away/far, far_away/lay), and they are let go again (far_away/sweep): no
# Train keeps a chunk loaded.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"far_away"}
function railworks:gallery/column {x:3,z:-6,top:1}
function railworks:gallery/column {x:9,z:-6,top:1}
function railworks:gallery/column {x:16,z:-6,top:1}
function railworks:gallery/column {x:21,z:-6,top:1}
railworks span ~3 ~ ~-6 ~9 ~ ~-6
railworks span ~9 ~ ~-6 ~16 ~ ~-6
railworks span ~16 ~ ~-6 ~21 ~ ~-6
fill ~4 ~ ~-8 ~4 ~2 ~-8 minecraft:stone_bricks
setblock ~4 ~3 ~-8 railworks:station{name:"Smelter"}
setblock ~11 ~ ~-4 minecraft:stone_bricks
setblock ~11 ~1 ~-4 minecraft:chest[facing=south]
setblock ~11 ~2 ~-4 minecraft:hopper[facing=down]
setblock ~11 ~3 ~-4 railworks:hatch[mode=requester]
railworks hatch ~11 ~3 ~-4 requester minecraft:iron_ore 2
# Set from the east, the first car over an Arm so it stands on its middle, each next coupled five on.
railworks car locomotive ~16 ~2 ~-6 -90
railworks car cargo_car ~11 ~2 ~-6 90
railworks car locomotive ~6 ~2 ~-6 90
# Coal for both Locomotives, wherever coupling set them, and the Train automatic.
execute align xyz positioned ~2 ~-1 ~-14 as @e[type=railworks:burner_locomotive,dx=39,dy=16,dz=14] run data merge entity @s {cab:[{id:"minecraft:coal",count:32}]}
execute align xyz positioned ~2 ~-1 ~-14 as @e[type=railworks:burner_locomotive,dx=39,dy=16,dz=14,limit=1] run railworks automatic @s
# A marker where the scene stands, in its dimension, which the far part is built from and around,
# since a scheduled function runs from no position of its own; one an earlier build left goes first.
execute as @e[type=minecraft:marker,tag=railworks.gallery.far_away,distance=..1] run kill @s
summon minecraft:marker ~ ~ ~ {Tags:["railworks.gallery.far_away"]}
forceload add ~22 ~-8 ~262 ~-4
schedule function railworks:gallery/far_away/wait 1t
