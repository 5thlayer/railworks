# Charging at a Station: the refuel scene's line and Stations, its shuttle of two Electric
# Locomotives standing at Mine with no charge. Creative Chargers stand beside Mine's leading car's
# place, where the Locomotive facing west stands, and beside its second car spot, where the one facing
# east stands, and fill both before the Train takes its first job. Beside Smelter's leading car's
# place, where the Locomotive facing east stands to unload, a Charger stands against a Burner
# Generator, which a hopper feeds coal from a chest: it charges that Locomotive each time it comes,
# with no cable, so the shuttle runs with nobody near it until Mine runs dry.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"charge"}
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
fill ~5 ~ ~-5 ~5 ~1 ~-5 minecraft:stone_bricks
setblock ~5 ~2 ~-5 railworks:creative_charger
fill ~9 ~ ~-5 ~9 ~1 ~-5 minecraft:stone_bricks
setblock ~9 ~2 ~-5 railworks:creative_charger
setblock ~22 ~ ~-7 minecraft:chest[facing=south]
setblock ~22 ~1 ~-7 minecraft:hopper[facing=down]
setblock ~22 ~2 ~-7 railworks:hatch[mode=requester]
railworks hatch ~22 ~2 ~-7 requester minecraft:iron_ore 2
fill ~23 ~ ~-7 ~23 ~1 ~-7 minecraft:stone_bricks
setblock ~23 ~2 ~-7 railworks:charger
fill ~23 ~ ~-8 ~23 ~1 ~-8 minecraft:stone_bricks
setblock ~23 ~2 ~-8 railworks:burner_generator[facing=north]
setblock ~23 ~3 ~-8 minecraft:hopper[facing=down]
setblock ~23 ~4 ~-8 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"minecraft:coal",count:64},{Slot:1b,id:"minecraft:coal",count:64}]}
railworks car cargo_car ~8 ~2 ~-6 90
railworks car electric_locomotive ~5 ~2 ~-6 90
railworks car electric_locomotive ~10 ~2 ~-6 -90
# The Train automatic, its Locomotives empty.
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:electric_locomotive,dx=23,dy=16,dz=12,limit=1] run railworks automatic @s
