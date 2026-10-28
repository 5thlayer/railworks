# Scheduled by the far-away scene's far part two ticks after it cut the old line: from each scene's
# marker whose line was cut and is not yet laid again.
execute as @e[type=minecraft:marker,tag=railworks.gallery.far_away.cut,tag=!railworks.gallery.far_away.built] at @s run function railworks:gallery/far_away/laid
