# Providers and requesters: a straight line between two Stations, Mine at its west end serving Trains
# heading west and Smelter at its east end serving Trains heading east. Beside Mine's first car spot a
# provider hatch holds eight stacks of iron ore; beside Smelter's a requester asks for two, and a
# hopper under it empties it into a chest, so it keeps asking. An automatic Train of a Locomotive
# facing out at each end and a Cargo car between them waits in the middle, takes the job and shuttles
# the ore across until Mine runs dry.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"logistics"}
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
setblock ~4 ~2 ~-7 railworks:station{name:"Mine"}
fill ~25 ~ ~-5 ~25 ~1 ~-5 minecraft:stone_bricks
setblock ~25 ~2 ~-5 railworks:station{name:"Smelter"}
fill ~7 ~ ~-5 ~7 ~1 ~-5 minecraft:stone_bricks
setblock ~7 ~2 ~-5 railworks:hatch[mode=provider]
railworks hatch ~7 ~2 ~-5 provider minecraft:iron_ore 8
setblock ~22 ~ ~-7 minecraft:chest[facing=south]
setblock ~22 ~1 ~-7 minecraft:hopper[facing=down]
setblock ~22 ~2 ~-7 railworks:hatch[mode=requester]
railworks hatch ~22 ~2 ~-7 requester minecraft:iron_ore 2
railworks car cargo_car ~13 ~2 ~-6 90
railworks car locomotive ~11 ~2 ~-6 90
railworks car locomotive ~15 ~2 ~-6 -90
# Coal for both Locomotives, wherever coupling set them, and the Train automatic.
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:burner_locomotive,dx=23,dy=16,dz=12] run data merge entity @s {cab:[{id:"minecraft:coal",count:32}]}
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:burner_locomotive,dx=23,dy=16,dz=12,limit=1] run railworks automatic @s
