# A load in each car: the logistics scene's line and Stations, Diggings and Foundry, with two Cargo
# cars between the Locomotives. Beside Diggings' first two car spots provider hatches hold raw copper
# and raw gold; beside Foundry's, requesters ask for each, emptied into chests below. The Train turns
# back at each end, so the car at Diggings' first spot stands at Foundry's second: the copper
# requester is beside the second spot and the gold one beside the first. Each car carries its own
# item.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"two_loads"}
function railworks:gallery/column {x:3,z:-6,top:1}
function railworks:gallery/column {x:9,z:-6,top:1}
function railworks:gallery/column {x:15,z:-6,top:1}
function railworks:gallery/column {x:21,z:-6,top:1}
function railworks:gallery/column {x:25,z:-6,top:1}
railworks span ~3 ~ ~-6 ~9 ~ ~-6
railworks span ~9 ~ ~-6 ~15 ~ ~-6
railworks span ~15 ~ ~-6 ~21 ~ ~-6
railworks span ~21 ~ ~-6 ~25 ~ ~-6
fill ~4 ~ ~-7 ~4 ~1 ~-7 minecraft:stone_bricks
setblock ~4 ~2 ~-7 railworks:station{name:"Diggings"}
fill ~25 ~ ~-5 ~25 ~1 ~-5 minecraft:stone_bricks
setblock ~25 ~2 ~-5 railworks:station{name:"Foundry"}
fill ~7 ~ ~-5 ~7 ~1 ~-5 minecraft:stone_bricks
setblock ~7 ~2 ~-5 railworks:hatch[mode=provider]
railworks hatch ~7 ~2 ~-5 provider minecraft:raw_copper 4
fill ~9 ~ ~-5 ~9 ~1 ~-5 minecraft:stone_bricks
setblock ~9 ~2 ~-5 railworks:hatch[mode=provider]
railworks hatch ~9 ~2 ~-5 provider minecraft:raw_gold 4
setblock ~20 ~ ~-7 minecraft:chest[facing=south]
setblock ~20 ~1 ~-7 minecraft:hopper[facing=down]
setblock ~20 ~2 ~-7 railworks:hatch[mode=requester]
railworks hatch ~20 ~2 ~-7 requester minecraft:raw_copper 1
setblock ~22 ~ ~-7 minecraft:chest[facing=south]
setblock ~22 ~1 ~-7 minecraft:hopper[facing=down]
setblock ~22 ~2 ~-7 railworks:hatch[mode=requester]
railworks hatch ~22 ~2 ~-7 requester minecraft:raw_gold 1
railworks car cargo_car ~12 ~2 ~-6 90
railworks car locomotive ~10 ~2 ~-6 90
railworks car cargo_car ~14 ~2 ~-6 90
railworks car locomotive ~16 ~2 ~-6 -90
# Coal for both Locomotives, wherever coupling set them, and the Train automatic.
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:burner_locomotive,dx=23,dy=16,dz=12] run data merge entity @s {cab:[{id:"minecraft:coal",count:32}]}
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:burner_locomotive,dx=23,dy=16,dz=12,limit=1] run railworks automatic @s
