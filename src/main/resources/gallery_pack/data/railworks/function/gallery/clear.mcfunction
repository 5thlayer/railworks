# Clears a scene's plot, from ~2 to ~25 east and from ~-12 to ~0 south, 16 high, onto a stone floor:
# its cars first, which takes them out of their Trains, then its blocks, whose Pylons going cut their
# Spans and whose Stations going take them out of the level's Stations, then the items dropped, the
# cars' loads and the Spans' Rail among them. The game tests run it on each plot before clearing away
# (libworks ADR 0010).
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:burner_locomotive,dx=23,dy=16,dz=12] run kill @s
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=railworks:cargo_car,dx=23,dy=16,dz=12] run kill @s
fill ~2 ~-1 ~-12 ~25 ~15 ~ minecraft:structure_void
fill ~2 ~-1 ~-12 ~25 ~-1 ~ minecraft:stone
fill ~2 ~ ~-12 ~25 ~15 ~ minecraft:air
execute align xyz positioned ~2 ~-1 ~-12 as @e[type=minecraft:item,dx=23,dy=16,dz=12] run kill @s
