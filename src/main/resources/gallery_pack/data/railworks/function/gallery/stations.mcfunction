# Stations: a line of two-segment columns and two named Stations, one on each side, each on a pillar
# in the row two blocks off the Rail at a Cargo car's chests' height. Each serves the Trains that have it on their drive side, so the
# two serve opposite ways, and its arrow points the way. They stand at either end of the line, each
# platform running back from its Station the way it serves, the leading car's place and two car
# spots, to where its Straight leg ends: the line steps north through two bends of 45 degrees in the
# middle, so the two platforms do not overlap.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"stations"}
function railworks:gallery/column {x:3,z:-6,top:1}
function railworks:gallery/column {x:11,z:-6,top:1}
function railworks:gallery/column {x:19,z:-6,top:1}
function railworks:gallery/column {x:21,z:-6,top:1}
function railworks:gallery/column {x:24,z:-9,top:1}
function railworks:gallery/column {x:26,z:-9,top:1}
function railworks:gallery/column {x:33,z:-9,top:1}
function railworks:gallery/column {x:41,z:-9,top:1}
railworks span ~3 ~ ~-6 ~11 ~ ~-6
railworks span ~11 ~ ~-6 ~19 ~ ~-6
railworks span ~19 ~ ~-6 ~21 ~ ~-6
railworks span ~21 ~ ~-6 ~24 ~ ~-9
railworks span ~24 ~ ~-9 ~26 ~ ~-9
railworks span ~26 ~ ~-9 ~33 ~ ~-9
railworks span ~33 ~ ~-9 ~41 ~ ~-9
fill ~4 ~ ~-8 ~4 ~2 ~-8 minecraft:stone_bricks
setblock ~4 ~3 ~-8 railworks:station{name:"Quarry"}
fill ~41 ~ ~-7 ~41 ~2 ~-7 minecraft:stone_bricks
setblock ~41 ~3 ~-7 railworks:station{name:"Smelter"}
