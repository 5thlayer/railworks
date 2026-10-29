# The line the shuttle scenes share, a U of two-segment columns: its south leg runs east along ~-2
# from its west end, turns north through four bends of 45 degrees at the plot's east side, and its
# north leg runs back west along ~-12 to its own west end. A Station stands at each leg's west end,
# on a pillar on the leg's north side, in the row two blocks off the Rail at a Cargo car's chests'
# height, so both serve Trains heading west: the $(south) on the south leg and the $(north) on the
# north. Each platform runs back east from its Station along its leg's 21 blocks of Straight leg,
# the leading car's place and three car spots, and ends where the leg's last Span bends into the U,
# so the two never overlap. A Train leaving one Station runs round the U and comes into the other
# heading west again.
function railworks:gallery/column {x:3,z:-2,top:1}
function railworks:gallery/column {x:9,z:-2,top:1}
function railworks:gallery/column {x:16,z:-2,top:1}
function railworks:gallery/column {x:24,z:-2,top:1}
function railworks:gallery/column {x:28,z:-2,top:1}
function railworks:gallery/column {x:32,z:-6,top:1}
function railworks:gallery/column {x:32,z:-8,top:1}
function railworks:gallery/column {x:28,z:-12,top:1}
function railworks:gallery/column {x:24,z:-12,top:1}
function railworks:gallery/column {x:16,z:-12,top:1}
function railworks:gallery/column {x:9,z:-12,top:1}
function railworks:gallery/column {x:3,z:-12,top:1}
railworks span ~3 ~ ~-2 ~9 ~ ~-2
railworks span ~9 ~ ~-2 ~16 ~ ~-2
railworks span ~16 ~ ~-2 ~24 ~ ~-2
railworks span ~24 ~ ~-2 ~28 ~ ~-2
railworks span ~28 ~ ~-2 ~32 ~ ~-6
railworks span ~32 ~ ~-6 ~32 ~ ~-8
railworks span ~32 ~ ~-8 ~28 ~ ~-12
railworks span ~28 ~ ~-12 ~24 ~ ~-12
railworks span ~24 ~ ~-12 ~16 ~ ~-12
railworks span ~16 ~ ~-12 ~9 ~ ~-12
railworks span ~9 ~ ~-12 ~3 ~ ~-12
fill ~4 ~ ~-4 ~4 ~2 ~-4 minecraft:stone_bricks
$setblock ~4 ~3 ~-4 railworks:station{name:"$(south)"}
fill ~4 ~ ~-14 ~4 ~2 ~-14 minecraft:stone_bricks
$setblock ~4 ~3 ~-14 railworks:station{name:"$(north)"}
