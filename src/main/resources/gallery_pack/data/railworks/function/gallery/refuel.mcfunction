# Refuelling at a Station: the logistics scene's line, Stations and hatches, its Train standing at Mine
# with nothing in either Locomotive's fuel slots. Provider hatches holding coal stand beside Mine's
# leading car's place, where the Locomotive facing west stands, and beside its second car spot, where
# the one facing east stands, each by the Locomotive's middle block. Mine fuels both before the Train
# takes its first job, and again each time it comes back for ore, so the shuttle runs with nobody near
# it until Mine runs dry.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"refuel"}
function railworks:gallery/shuttle {south:"Mine",north:"Smelter"}
fill ~11 ~ ~-1 ~11 ~1 ~-1 minecraft:stone_bricks
setblock ~11 ~2 ~-1 railworks:hatch[mode=provider]
railworks hatch ~11 ~2 ~-1 provider minecraft:iron_ore 8
fill ~6 ~ ~-1 ~6 ~1 ~-1 minecraft:stone_bricks
setblock ~6 ~2 ~-1 railworks:hatch[mode=provider]
railworks hatch ~6 ~2 ~-1 provider minecraft:coal 8
fill ~16 ~ ~-1 ~16 ~1 ~-1 minecraft:stone_bricks
setblock ~16 ~2 ~-1 railworks:hatch[mode=provider]
railworks hatch ~16 ~2 ~-1 provider minecraft:coal 8
setblock ~11 ~ ~-12 minecraft:chest[facing=south]
setblock ~11 ~1 ~-12 minecraft:hopper[facing=down]
setblock ~11 ~2 ~-12 railworks:hatch[mode=requester]
railworks hatch ~11 ~2 ~-12 requester minecraft:iron_ore 2
# Set from the east, the first car over an Arm so it stands on its middle, each next coupled five on.
railworks car locomotive ~16 ~2 ~-2 -90
railworks car cargo_car ~11 ~2 ~-2 90
railworks car locomotive ~6 ~2 ~-2 90
# The Train automatic, its Locomotives dry.
execute align xyz positioned ~2 ~-1 ~-14 as @e[type=railworks:burner_locomotive,dx=39,dy=16,dz=14,limit=1] run railworks automatic @s
