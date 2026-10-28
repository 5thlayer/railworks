# The Railworks Gallery: a scene of each of the Module's features, each building and clearing a plot
# of its own, 24 blocks wide and 13 deep. The scenes stand in rows of three, read west to east and
# then north, each row 16 blocks north of the one before, so the whole gallery stays near where it is
# run. The third row is logistics, Trains running jobs from provider hatches to requesters, and then
# the flyover; the fourth begins with a Train fuelled at its Station, then one charged at its Stations,
# and ends with one whose line runs out east past the gallery and the chunks loaded around it, last in
# its row so that line crosses no other plot.
function railworks:gallery/pylons
execute positioned ~24 ~ ~ run function railworks:gallery/limits
execute positioned ~48 ~ ~ run function railworks:gallery/curves
execute positioned ~ ~ ~-16 run function railworks:gallery/locomotive
execute positioned ~24 ~ ~-16 run function railworks:gallery/coupling
execute positioned ~48 ~ ~-16 run function railworks:gallery/stations
execute positioned ~ ~ ~-32 run function railworks:gallery/logistics
execute positioned ~24 ~ ~-32 run function railworks:gallery/two_loads
execute positioned ~48 ~ ~-32 run function railworks:gallery/flyover
execute positioned ~ ~ ~-48 run function railworks:gallery/refuel
execute positioned ~24 ~ ~-48 run function railworks:gallery/charge
execute positioned ~48 ~ ~-48 run function railworks:gallery/far_away
