# Scheduled by the far-away scene: from each scene's marker its old line is not yet cut from, in
# its own dimension, builds the far part once the chunks along its line have loaded, or waits a tick.
execute as @e[type=minecraft:marker,tag=railworks.gallery.far_away,tag=!railworks.gallery.far_away.cut] at @s run function railworks:gallery/far_away/at
