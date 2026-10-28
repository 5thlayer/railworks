# Pylons and Rail: four columns of one to four segments in a line running east, each Arm strung to
# the next, the two in the middle holding two Spans each. The chest holds Pylons and Rail: a Pylon
# placed west of the line strings itself to its end.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"pylons"}
function railworks:gallery/column {x:14,z:-6,top:0}
function railworks:gallery/column {x:19,z:-6,top:1}
function railworks:gallery/column {x:24,z:-6,top:2}
function railworks:gallery/column {x:29,z:-6,top:3}
railworks span ~14 ~ ~-6 ~19 ~ ~-6
railworks span ~19 ~ ~-6 ~24 ~ ~-6
railworks span ~24 ~ ~-6 ~29 ~ ~-6
setblock ~10 ~ ~-2 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"railworks:pylon",count:16},{Slot:1b,id:"railworks:rail",count:64}]}
