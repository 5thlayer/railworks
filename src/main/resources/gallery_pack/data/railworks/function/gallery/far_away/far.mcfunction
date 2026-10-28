# The far-away scene's far part, run at its marker. What an earlier build left out there goes first,
# broken as clear breaks a plot: its Pylons, so its Spans are cut, its Station, which leaves the
# level's Stations, and its hatch, which takes its record, where this build stands it and where
# earlier ones did. A Train an earlier build set running stands on that line out of the plot clear
# cleared, so the line is laid again only two ticks on (far_away/lay), once that Train has come off
# the cut Rail.
setblock ~253 ~2 ~-7 minecraft:air destroy
setblock ~257 ~2 ~-7 minecraft:air destroy
setblock ~258 ~2 ~-7 minecraft:air destroy
setblock ~260 ~2 ~-5 minecraft:air destroy
fill ~51 ~ ~-6 ~261 ~1 ~-6 minecraft:air destroy
tag @s add railworks.gallery.far_away.cut
schedule function railworks:gallery/far_away/lay 2t
