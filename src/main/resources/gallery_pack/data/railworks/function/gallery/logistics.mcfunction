# Providers and requesters: the shuttle line (gallery/shuttle), Mine at its south leg's west end and
# Smelter at its north leg's. Beside Mine's first car spot a provider hatch holds eight stacks of iron
# ore; beside Smelter's a requester asks for two, and a hopper under it empties it into a chest, so it
# keeps asking. An automatic Train of a Locomotive facing out at each end and a Cargo car between
# them, five blocks apart, waits at Mine, takes the job and shuttles the ore round the line until Mine
# runs dry.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"logistics"}
function railworks:gallery/shuttle {south:"Mine",north:"Smelter"}
fill ~11 ~ ~-1 ~11 ~1 ~-1 minecraft:stone_bricks
setblock ~11 ~2 ~-1 railworks:hatch[mode=provider]
railworks hatch ~11 ~2 ~-1 provider minecraft:iron_ore 8
setblock ~11 ~ ~-12 minecraft:chest[facing=south]
setblock ~11 ~1 ~-12 minecraft:hopper[facing=down]
setblock ~11 ~2 ~-12 railworks:hatch[mode=requester]
railworks hatch ~11 ~2 ~-12 requester minecraft:iron_ore 2
# Set from the east, the first car over an Arm so it stands on its middle, each next coupled five on.
railworks car locomotive ~16 ~2 ~-2 -90
railworks car cargo_car ~11 ~2 ~-2 90
railworks car locomotive ~6 ~2 ~-2 90
# Coal for both Locomotives, wherever coupling set them, and the Train automatic.
execute align xyz positioned ~2 ~-1 ~-14 as @e[type=railworks:burner_locomotive,dx=39,dy=16,dz=14] run data merge entity @s {cab:[{id:"minecraft:coal",count:32}]}
execute align xyz positioned ~2 ~-1 ~-14 as @e[type=railworks:burner_locomotive,dx=39,dy=16,dz=14,limit=1] run railworks automatic @s
