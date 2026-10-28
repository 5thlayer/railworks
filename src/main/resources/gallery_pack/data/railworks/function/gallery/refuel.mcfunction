# Refuelling at a Station: the logistics scene's line, Stations and hatches, its Train standing at Mine
# with nothing in either Locomotive's fuel slots. Provider hatches holding coal stand beside Mine's
# leading car's place, where the Locomotive facing west stands, and beside its second car spot, where
# the one facing east stands. Mine fuels both before the Train takes its first job, and again each
# time it comes back for ore, so the shuttle runs with nobody near it until Mine runs dry.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"refuel"}
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
fill ~24 ~ ~-5 ~24 ~1 ~-5 minecraft:stone_bricks
setblock ~24 ~2 ~-5 railworks:station{name:"Smelter"}
fill ~7 ~ ~-5 ~7 ~1 ~-5 minecraft:stone_bricks
setblock ~7 ~2 ~-5 railworks:hatch[mode=provider]
railworks hatch ~7 ~2 ~-5 provider minecraft:iron_ore 8
fill ~5 ~ ~-5 ~5 ~1 ~-5 minecraft:stone_bricks
setblock ~5 ~2 ~-5 railworks:hatch[mode=provider]
railworks hatch ~5 ~2 ~-5 provider minecraft:coal 8
fill ~9 ~ ~-5 ~9 ~1 ~-5 minecraft:stone_bricks
setblock ~9 ~2 ~-5 railworks:hatch[mode=provider]
railworks hatch ~9 ~2 ~-5 provider minecraft:coal 8
setblock ~22 ~ ~-7 minecraft:chest[facing=south]
setblock ~22 ~1 ~-7 minecraft:hopper[facing=down]
setblock ~22 ~2 ~-7 railworks:hatch[mode=requester]
railworks hatch ~22 ~2 ~-7 requester minecraft:iron_ore 2
railworks car cargo_car ~7 ~2 ~-6 90
railworks car locomotive ~5 ~2 ~-6 90
railworks car locomotive ~9 ~2 ~-6 -90
# The Train automatic, its Locomotives dry.
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:burner_locomotive,dx=23,dy=16,dz=12,limit=1] run railworks automatic @s
