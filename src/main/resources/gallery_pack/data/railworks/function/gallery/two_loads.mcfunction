# A load in each car: the shuttle line (gallery/shuttle), Diggings and Foundry, with two Cargo cars
# between the Locomotives. Beside Diggings' first two car spots provider hatches hold raw copper and
# raw gold; beside Foundry's, requesters ask for each, emptied into chests below. The Train comes into
# Foundry led by the Locomotive it left Diggings with at its back, so the car at Diggings' first spot
# stands at Foundry's second: the copper requester is beside the second spot and the gold one beside
# the first. Each car carries its own item.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"two_loads"}
function railworks:gallery/shuttle {south:"Diggings",north:"Foundry"}
fill ~11 ~ ~ ~11 ~2 ~ minecraft:stone_bricks
setblock ~11 ~3 ~ railworks:hatch[mode=provider]
railworks hatch ~11 ~3 ~ provider minecraft:raw_copper 4
fill ~16 ~ ~ ~16 ~2 ~ minecraft:stone_bricks
setblock ~16 ~3 ~ railworks:hatch[mode=provider]
railworks hatch ~16 ~3 ~ provider minecraft:raw_gold 4
setblock ~16 ~ ~-10 minecraft:stone_bricks
setblock ~16 ~1 ~-10 minecraft:chest[facing=south]
setblock ~16 ~2 ~-10 minecraft:hopper[facing=down]
setblock ~16 ~3 ~-10 railworks:hatch[mode=requester]
railworks hatch ~16 ~3 ~-10 requester minecraft:raw_copper 1
setblock ~11 ~ ~-10 minecraft:stone_bricks
setblock ~11 ~1 ~-10 minecraft:chest[facing=south]
setblock ~11 ~2 ~-10 minecraft:hopper[facing=down]
setblock ~11 ~3 ~-10 railworks:hatch[mode=requester]
railworks hatch ~11 ~3 ~-10 requester minecraft:raw_gold 1
# The first car set over an Arm so it stands on its middle, each next coupled five on.
railworks car cargo_car ~16 ~2 ~-2 -90
railworks car cargo_car ~11 ~2 ~-2 90
railworks car locomotive ~6 ~2 ~-2 90
railworks car locomotive ~21 ~2 ~-2 -90
# Coal for both Locomotives, wherever coupling set them, and the Train automatic.
execute align xyz positioned ~2 ~-1 ~-14 as @e[type=railworks:burner_locomotive,dx=39,dy=16,dz=14] run data merge entity @s {cab:[{id:"minecraft:coal",count:32}]}
execute align xyz positioned ~2 ~-1 ~-14 as @e[type=railworks:burner_locomotive,dx=39,dy=16,dz=14,limit=1] run railworks automatic @s
