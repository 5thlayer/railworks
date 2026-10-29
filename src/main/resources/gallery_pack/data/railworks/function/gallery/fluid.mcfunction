# A Fluid car: the logistics scene's line and Stations, Mine and Smelter, with a Fluid car between the
# Locomotives. Beside Mine's first car spot a provider Fluid hatch holds 32 buckets of lava; beside
# Smelter's a requester Fluid hatch asks for 16. The automatic Train takes the job, the Fluid
# hatch filling the car's tank two buckets a turn, and carries the lava round, its status line showing
# a lava bucket and the buckets it carries. Fluid hatches trade only with a Fluid car, and a Fluid car
# fills by hand a bucket at a time.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"fluid"}
function railworks:gallery/shuttle {south:"Mine",north:"Smelter"}
fill ~11 ~ ~ ~11 ~2 ~ minecraft:stone_bricks
setblock ~11 ~3 ~ railworks:fluid_hatch[mode=provider]
railworks fluid_hatch ~11 ~3 ~ provider minecraft:lava 32
fill ~11 ~ ~-10 ~11 ~2 ~-10 minecraft:stone_bricks
setblock ~11 ~3 ~-10 railworks:fluid_hatch[mode=requester]
railworks fluid_hatch ~11 ~3 ~-10 requester minecraft:lava 16
# Set from the east, the first car over an Arm so it stands on its middle, each next coupled five on.
railworks car locomotive ~16 ~2 ~-2 -90
railworks car fluid_car ~11 ~2 ~-2 90
railworks car locomotive ~6 ~2 ~-2 90
# Coal for both Locomotives, wherever coupling set them, and the Train automatic.
execute align xyz positioned ~2 ~-1 ~-14 as @e[type=railworks:burner_locomotive,dx=39,dy=16,dz=14] run data merge entity @s {cab:[{id:"minecraft:coal",count:32}]}
execute align xyz positioned ~2 ~-1 ~-14 as @e[type=railworks:burner_locomotive,dx=39,dy=16,dz=14,limit=1] run railworks automatic @s
