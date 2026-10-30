# The Railworks Gallery: a scene of each of the Module's features, each building and clearing a plot
# of its own, 40 blocks wide and 15 deep, room for a Train of five-block cars and the platforms it
# stops at, and gallery/clear clears a block past its west end and two past its east, where its
# lines' Clearance reaches. The scenes stand one a row, each row 16 blocks north of the one before:
# libworks' GalleryCheck lays the plots of a row 24 blocks apart, too close for plots this wide, so
# each row holds one. Logistics, Trains running jobs from provider hatches to requesters, comes after
# the Stations, then the flyover, a Train fuelled at its Station and one charged at its Stations,
# then one whose line runs out east past the gallery and the chunks loaded around it, which no plot
# stands east of, and last a Fluid car carrying lava.
function railworks:gallery/pylons
execute positioned ~ ~ ~-16 run function railworks:gallery/limits
execute positioned ~ ~ ~-32 run function railworks:gallery/curves
execute positioned ~ ~ ~-48 run function railworks:gallery/locomotive
execute positioned ~ ~ ~-64 run function railworks:gallery/coupling
execute positioned ~ ~ ~-80 run function railworks:gallery/stations
execute positioned ~ ~ ~-96 run function railworks:gallery/logistics
execute positioned ~ ~ ~-112 run function railworks:gallery/two_loads
execute positioned ~ ~ ~-128 run function railworks:gallery/flyover
execute positioned ~ ~ ~-144 run function railworks:gallery/refuel
execute positioned ~ ~ ~-160 run function railworks:gallery/charge
execute positioned ~ ~ ~-176 run function railworks:gallery/far_away
execute positioned ~ ~ ~-192 run function railworks:gallery/fluid
