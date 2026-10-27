# A flyover: two lines crossing at different heights without joining (ADR 0002). The low line, of
# two-segment columns, runs east with a Locomotive with coal at its west end; the high line, a single
# Span between two seven-segment columns, crosses over it heading north, its Rail two blocks clear of
# a Locomotive's roof on the low line, with a Locomotive with coal at its south end, facing north.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"flyover"}
function railworks:gallery/column {x:3,z:-6,top:1}
function railworks:gallery/column {x:11,z:-6,top:1}
function railworks:gallery/column {x:19,z:-6,top:1}
function railworks:gallery/column {x:25,z:-6,top:1}
railworks span ~3 ~ ~-6 ~11 ~ ~-6
railworks span ~11 ~ ~-6 ~19 ~ ~-6
railworks span ~19 ~ ~-6 ~25 ~ ~-6
function railworks:gallery/column {x:15,z:-1,top:6}
function railworks:gallery/column {x:15,z:-12,top:6}
railworks span ~15 ~ ~-1 ~15 ~ ~-12
railworks car locomotive ~6 ~2 ~-6 -90
execute positioned ~6.5 ~2 ~-5.5 run data merge entity @e[type=railworks:burner_locomotive,distance=..2,limit=1] {cab:[{id:"minecraft:coal",count:32}]}
railworks car locomotive ~15 ~7 ~-4 180
execute positioned ~15.5 ~7 ~-3.5 run data merge entity @e[type=railworks:burner_locomotive,distance=..2,limit=1] {cab:[{id:"minecraft:coal",count:32}]}
