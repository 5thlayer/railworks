# Curves: a line of two-segment columns bending right through 45 degrees and back again, the Rail
# drawn as a smooth curve through each Arm, and a Locomotive with coal at its west end, facing east,
# to ride it round.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"curves"}
function railworks:gallery/column {x:3,z:-11,top:1}
function railworks:gallery/column {x:13,z:-11,top:1}
function railworks:gallery/column {x:21,z:-3,top:1}
function railworks:gallery/column {x:31,z:-3,top:1}
function railworks:gallery/column {x:41,z:-3,top:1}
railworks span ~3 ~ ~-11 ~13 ~ ~-11
railworks span ~13 ~ ~-11 ~21 ~ ~-3
railworks span ~21 ~ ~-3 ~31 ~ ~-3
railworks span ~31 ~ ~-3 ~41 ~ ~-3
railworks car locomotive ~6 ~2 ~-11 -90
execute positioned ~6.5 ~2 ~-10.5 run data merge entity @e[type=railworks:burner_locomotive,distance=..2,limit=1] {cab:[{id:"minecraft:coal",count:32}]}
