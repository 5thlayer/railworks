# Charging at a Station: the refuel scene's line and Stations, its shuttle of two Electric
# Locomotives standing at Mine with no charge. Creative Chargers stand beside Mine's leading car's
# place, where the Locomotive facing west stands, and beside its second car spot, where the one
# facing east stands, and fill both before the Train takes its first job. Beside Smelter's leading
# car's place, where the Locomotive that led the Train round the line stands to unload, a Charger
# stands against a Burner Generator behind it, away from the Rail, which a hopper feeds coal from a
# chest: it charges that Locomotive each time it comes, with no cable, so the shuttle runs with
# nobody near it until Mine runs dry.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"charge"}
function railworks:gallery/shuttle {south:"Mine",north:"Smelter"}
fill ~11 ~ ~ ~11 ~2 ~ minecraft:stone_bricks
setblock ~11 ~3 ~ railworks:hatch[mode=provider]
railworks hatch ~11 ~3 ~ provider minecraft:iron_ore 8
fill ~6 ~ ~ ~6 ~2 ~ minecraft:stone_bricks
setblock ~6 ~3 ~ railworks:creative_charger
fill ~16 ~ ~ ~16 ~2 ~ minecraft:stone_bricks
setblock ~16 ~3 ~ railworks:creative_charger
setblock ~11 ~ ~-10 minecraft:stone_bricks
setblock ~11 ~1 ~-10 minecraft:chest[facing=south]
setblock ~11 ~2 ~-10 minecraft:hopper[facing=down]
setblock ~11 ~3 ~-10 railworks:hatch[mode=requester]
railworks hatch ~11 ~3 ~-10 requester minecraft:iron_ore 2
fill ~6 ~ ~-10 ~6 ~2 ~-10 minecraft:stone_bricks
setblock ~6 ~3 ~-10 railworks:charger
fill ~6 ~ ~-9 ~6 ~2 ~-9 minecraft:stone_bricks
setblock ~6 ~3 ~-9 railworks:burner_generator[facing=south]
setblock ~6 ~4 ~-9 minecraft:hopper[facing=down]
setblock ~6 ~5 ~-9 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:coal",count:64},{Slot:1b,id:"minecraft:coal",count:64}]}
# Set from the east, the first car over an Arm so it stands on its middle, each next coupled five on.
railworks car electric_locomotive ~16 ~2 ~-2 -90
railworks car cargo_car ~11 ~2 ~-2 90
railworks car electric_locomotive ~6 ~2 ~-2 90
# The Train automatic, its Locomotives empty.
execute align xyz positioned ~2 ~-1 ~-14 as @e[type=railworks:electric_locomotive,dx=39,dy=16,dz=14,limit=1] run railworks automatic @s
