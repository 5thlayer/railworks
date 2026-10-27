# Curves: a line of two-segment columns bending right through 45 degrees and back again, the Rail
# drawn as a smooth curve through each Arm, and a Locomotive with coal at its west end, facing east,
# to ride it round.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"curves"}
function railworks:gallery/column {x:3,z:-9,top:1}
function railworks:gallery/column {x:9,z:-9,top:1}
function railworks:gallery/column {x:14,z:-4,top:1}
function railworks:gallery/column {x:20,z:-4,top:1}
function railworks:gallery/column {x:25,z:-4,top:1}
railworks span ~3 ~ ~-9 ~9 ~ ~-9
railworks span ~9 ~ ~-9 ~14 ~ ~-4
railworks span ~14 ~ ~-4 ~20 ~ ~-4
railworks span ~20 ~ ~-4 ~25 ~ ~-4
railworks car locomotive ~5 ~2 ~-9 -90
execute positioned ~5.5 ~2 ~-8.5 run data merge entity @e[type=railworks:burner_locomotive,distance=..2,limit=1] {cab:[{id:"minecraft:coal",count:32}]}
