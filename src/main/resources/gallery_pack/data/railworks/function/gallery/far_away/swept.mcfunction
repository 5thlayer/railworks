# Run at a far-away scene's marker once its far part has stood a second: what was broken along the
# line goes, the Rail of the Spans cut and the cars of an earlier build's Train that came off it as
# their chunks ticked, then the chunks are let go of, and the marker with them.
execute align xyz positioned ~22 ~-1 ~-8 run kill @e[type=minecraft:item,dx=240,dy=8,dz=4]
forceload remove ~22 ~-8 ~262 ~-4
kill @s
