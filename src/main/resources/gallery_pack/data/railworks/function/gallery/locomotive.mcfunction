# The Burner Locomotive: a straight line of two-segment columns and a Locomotive with coal at its west
# end, facing east, to board and drive.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"locomotive"}
function railworks:gallery/column {x:3,z:-6,top:1}
function railworks:gallery/column {x:11,z:-6,top:1}
function railworks:gallery/column {x:19,z:-6,top:1}
function railworks:gallery/column {x:27,z:-6,top:1}
function railworks:gallery/column {x:35,z:-6,top:1}
function railworks:gallery/column {x:41,z:-6,top:1}
railworks span ~3 ~ ~-6 ~11 ~ ~-6
railworks span ~11 ~ ~-6 ~19 ~ ~-6
railworks span ~19 ~ ~-6 ~27 ~ ~-6
railworks span ~27 ~ ~-6 ~35 ~ ~-6
railworks span ~35 ~ ~-6 ~41 ~ ~-6
railworks car locomotive ~6 ~2 ~-6 -90
execute positioned ~6.5 ~2 ~-5.5 run data merge entity @e[type=railworks:burner_locomotive,distance=..2,limit=1] {cab:[{id:"minecraft:coal",count:32}]}
