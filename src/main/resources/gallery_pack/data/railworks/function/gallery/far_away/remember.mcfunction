# Run as the far-away scene's marker, which stands on a block's corner: keeps where it stands in
# storage, whole blocks, for the far part to build from, and goes.
execute store result storage railworks:gallery far_away.x int 1 run data get entity @s Pos[0]
execute store result storage railworks:gallery far_away.y int 1 run data get entity @s Pos[1]
execute store result storage railworks:gallery far_away.z int 1 run data get entity @s Pos[2]
kill @s
