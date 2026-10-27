# A scene's sign at ~14 ~ ~0, facing south, waxed. Its name is in bold, and clicking it runs the
# scene's function again from the plot's corner, 14 blocks west of the sign, which builds it afresh.
$setblock ~14 ~ ~ minecraft:oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:[{translate:"railworks.gallery.$(scene)",bold:true,click_event:{action:"run_command",command:"execute positioned ~-14 ~ ~ run function railworks:gallery/$(scene)"}},{translate:"railworks.gallery.$(scene).1"},{translate:"railworks.gallery.$(scene).2"},{translate:"railworks.gallery.$(scene).3"}]}}
