# A Fluid car: the logistics scene's line and Stations, Mine and Smelter, with a Fluid car between the
# Locomotives. Beside Mine's first car spot a provider Fluid hatch holds 32 buckets of lava; beside
# Smelter's a requester Fluid hatch asks for 16. The automatic Train takes the job, the Fluid
# hatch filling the car's tank two buckets a turn, and carries the lava across, its status line showing
# a lava bucket and the buckets it carries. Fluid hatches trade only with a Fluid car, and a Fluid car
# fills by hand a bucket at a time.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"fluid"}
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
setblock ~7 ~2 ~-5 railworks:fluid_hatch[mode=provider]
railworks fluid_hatch ~7 ~2 ~-5 provider minecraft:lava 32
fill ~22 ~ ~-7 ~22 ~1 ~-7 minecraft:stone_bricks
setblock ~22 ~2 ~-7 railworks:fluid_hatch[mode=requester]
railworks fluid_hatch ~22 ~2 ~-7 requester minecraft:lava 16
railworks car fluid_car ~13 ~2 ~-6 90
railworks car locomotive ~11 ~2 ~-6 90
railworks car locomotive ~15 ~2 ~-6 -90
# Coal for both Locomotives, wherever coupling set them, and the Train automatic.
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:burner_locomotive,dx=23,dy=16,dz=12] run data merge entity @s {cab:[{id:"minecraft:coal",count:32}]}
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:burner_locomotive,dx=23,dy=16,dz=12,limit=1] run railworks automatic @s
