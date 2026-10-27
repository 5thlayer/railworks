# The grade and bend limits. In front, a Span climbing two blocks over eight, the steepest there is
# (25%), and past its top an unstrung column a block higher three blocks on, too steep to string to.
# Behind, a line turning through 45 degrees at an Arm, the sharpest bend there is, and past its end an
# unstrung Pylon the line would turn 56 degrees to. The chest holds Rail to try both.
function railworks:gallery/clear
function railworks:gallery/sign {scene:"limits"}
function railworks:gallery/column {x:3,z:-2,top:0}
function railworks:gallery/column {x:11,z:-2,top:2}
function railworks:gallery/column {x:14,z:-2,top:3}
railworks span ~3 ~ ~-2 ~11 ~ ~-2
function railworks:gallery/column {x:3,z:-10,top:0}
function railworks:gallery/column {x:11,z:-10,top:0}
function railworks:gallery/column {x:16,z:-5,top:0}
function railworks:gallery/column {x:21,z:-6,top:0}
railworks span ~3 ~ ~-10 ~11 ~ ~-10
railworks span ~11 ~ ~-10 ~16 ~ ~-5
setblock ~22 ~ ~-2 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"railworks:rail",count:64}]}
