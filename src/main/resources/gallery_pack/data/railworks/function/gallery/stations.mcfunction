# Stations: a straight line of two-segment columns and two named Stations beside the Rail at a car's
# floor, one on each side, each on a pillar. Each serves the Trains that have it on their drive side,
# so the two serve opposite ways, and its arrow points the way.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"stations"}
function railworks:gallery/column {x:3,z:-6,top:1}
function railworks:gallery/column {x:9,z:-6,top:1}
function railworks:gallery/column {x:15,z:-6,top:1}
function railworks:gallery/column {x:21,z:-6,top:1}
function railworks:gallery/column {x:25,z:-6,top:1}
railworks span ~3 ~ ~-6 ~9 ~ ~-6
railworks span ~9 ~ ~-6 ~15 ~ ~-6
railworks span ~15 ~ ~-6 ~21 ~ ~-6
railworks span ~21 ~ ~-6 ~25 ~ ~-6
fill ~5 ~ ~-7 ~5 ~1 ~-7 minecraft:stone_bricks
setblock ~5 ~2 ~-7 railworks:station{name:"Quarry"}
fill ~23 ~ ~-5 ~23 ~1 ~-5 minecraft:stone_bricks
setblock ~23 ~2 ~-5 railworks:station{name:"Smelter"}
