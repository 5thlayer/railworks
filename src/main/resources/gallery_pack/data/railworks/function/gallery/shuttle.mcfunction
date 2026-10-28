# The line the shuttle scenes share, a U of two-segment columns: its south leg runs east along ~-2
# from its west end, turns north through four bends of 45 degrees at the plot's east side, and its
# north leg runs back west along ~-13 to its own west end. A Station stands at each leg's west end,
# beside the Rail at a car's floor on the leg's north side, so both serve Trains heading west: the
# $(south) on the south leg and the $(north) on the north. Each platform runs back east from its
# Station along its leg's 21 blocks of Straight leg, the leading car's place and three car spots, and
# ends where the leg's last Span bends into the U, so the two never overlap. A Train leaving one
# Station runs round the U and comes into the other heading west again.
function railworks:gallery/column {x:3,z:-2,top:1}
function railworks:gallery/column {x:9,z:-2,top:1}
function railworks:gallery/column {x:16,z:-2,top:1}
function railworks:gallery/column {x:24,z:-2,top:1}
function railworks:gallery/column {x:28,z:-2,top:1}
function railworks:gallery/column {x:32,z:-6,top:1}
function railworks:gallery/column {x:32,z:-9,top:1}
function railworks:gallery/column {x:28,z:-13,top:1}
function railworks:gallery/column {x:24,z:-13,top:1}
function railworks:gallery/column {x:16,z:-13,top:1}
function railworks:gallery/column {x:9,z:-13,top:1}
function railworks:gallery/column {x:3,z:-13,top:1}
railworks span ~3 ~ ~-2 ~9 ~ ~-2
railworks span ~9 ~ ~-2 ~16 ~ ~-2
railworks span ~16 ~ ~-2 ~24 ~ ~-2
railworks span ~24 ~ ~-2 ~28 ~ ~-2
railworks span ~28 ~ ~-2 ~32 ~ ~-6
railworks span ~32 ~ ~-6 ~32 ~ ~-9
railworks span ~32 ~ ~-9 ~28 ~ ~-13
railworks span ~28 ~ ~-13 ~24 ~ ~-13
railworks span ~24 ~ ~-13 ~16 ~ ~-13
railworks span ~16 ~ ~-13 ~9 ~ ~-13
railworks span ~9 ~ ~-13 ~3 ~ ~-13
fill ~4 ~ ~-3 ~4 ~1 ~-3 minecraft:stone_bricks
$setblock ~4 ~2 ~-3 railworks:station{name:"$(south)"}
fill ~4 ~ ~-14 ~4 ~1 ~-14 minecraft:stone_bricks
$setblock ~4 ~2 ~-14 railworks:station{name:"$(north)"}
