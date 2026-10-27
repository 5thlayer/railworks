# Pylons and Rail: four columns of one to four segments in a line running east, each Arm strung to
# the next, the two in the middle holding two Spans each. The chest holds Pylons and Rail: a Pylon
# placed west of the line strings itself to its end.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"pylons"}
function railworks:gallery/column {x:8,z:-6,top:0}
function railworks:gallery/column {x:13,z:-6,top:1}
function railworks:gallery/column {x:18,z:-6,top:2}
function railworks:gallery/column {x:23,z:-6,top:3}
railworks span ~8 ~ ~-6 ~13 ~ ~-6
railworks span ~13 ~ ~-6 ~18 ~ ~-6
railworks span ~18 ~ ~-6 ~23 ~ ~-6
setblock ~4 ~ ~-2 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"railworks:pylon",count:16},{Slot:1b,id:"railworks:rail",count:64}]}
