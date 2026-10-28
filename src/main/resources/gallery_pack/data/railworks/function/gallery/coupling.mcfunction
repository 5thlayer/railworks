# Cargo cars and coupling: a Train at rest of a Locomotive with coal facing east and two Cargo cars
# coupled behind it, each set beside the end of the Train as a player would, five blocks apart. The
# chest holds a spare Cargo car and Locomotive to couple on, and shears to part it.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"coupling"}
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
railworks car locomotive ~16 ~2 ~-6 -90
railworks car cargo_car ~11 ~2 ~-6 -90
railworks car cargo_car ~6 ~2 ~-6 -90
execute positioned ~16.5 ~2 ~-5.5 run data merge entity @e[type=railworks:burner_locomotive,distance=..1,limit=1] {cab:[{id:"minecraft:coal",count:32}]}
setblock ~16 ~ ~-2 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"railworks:cargo_car",count:1},{Slot:1b,id:"railworks:burner_locomotive",count:1},{Slot:2b,id:"minecraft:shears",count:1},{Slot:3b,id:"minecraft:coal",count:16}]}
