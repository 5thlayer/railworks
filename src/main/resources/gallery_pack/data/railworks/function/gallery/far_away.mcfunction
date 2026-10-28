# Trains far away: the logistics scene's Mine and its provider of iron ore, its line running on east
# out of the plot for 240 blocks, past the chunks a player standing at the sign has loaded, to Depot,
# whose requester asks for two stacks. The automatic Train runs its job out there with no chunk of its
# way loaded, and comes back to the Mine for more. Commands build only in loaded chunks, so the line's
# chunks are force-loaded, the far part is built once they have loaded (far_away/far), and they are
# let go again (far_away/sweep): no Train keeps a chunk loaded.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"far_away"}
function railworks:gallery/column {x:3,z:-6,top:1}
function railworks:gallery/column {x:9,z:-6,top:1}
function railworks:gallery/column {x:15,z:-6,top:1}
function railworks:gallery/column {x:21,z:-6,top:1}
railworks span ~3 ~ ~-6 ~9 ~ ~-6
railworks span ~9 ~ ~-6 ~15 ~ ~-6
railworks span ~15 ~ ~-6 ~21 ~ ~-6
fill ~4 ~ ~-7 ~4 ~1 ~-7 minecraft:stone_bricks
setblock ~4 ~2 ~-7 railworks:station{name:"Mine"}
fill ~7 ~ ~-5 ~7 ~1 ~-5 minecraft:stone_bricks
setblock ~7 ~2 ~-5 railworks:hatch[mode=provider]
railworks hatch ~7 ~2 ~-5 provider minecraft:iron_ore 8
railworks car cargo_car ~13 ~2 ~-6 90
railworks car locomotive ~11 ~2 ~-6 90
railworks car locomotive ~15 ~2 ~-6 -90
# Coal for both Locomotives, wherever coupling set them, and the Train automatic.
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:burner_locomotive,dx=23,dy=16,dz=12] run data merge entity @s {cab:[{id:"minecraft:coal",count:32}]}
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:burner_locomotive,dx=23,dy=16,dz=12,limit=1] run railworks automatic @s
# A marker where the scene stands, in its dimension, which the far part is built from and around,
# since a scheduled function runs from no position of its own; one an earlier build left goes first.
execute as @e[type=minecraft:marker,tag=railworks.gallery.far_away,distance=..1] run kill @s
summon minecraft:marker ~ ~ ~ {Tags:["railworks.gallery.far_away"]}
forceload add ~22 ~-8 ~262 ~-4
schedule function railworks:gallery/far_away/wait 1t
