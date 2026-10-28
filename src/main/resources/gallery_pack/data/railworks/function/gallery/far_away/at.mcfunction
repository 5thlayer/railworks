# Run at a far-away scene's marker: builds its far part if every chunk its line crosses has loaded,
# or waits a tick more.
execute if loaded ~51 ~ ~-6 if loaded ~81 ~ ~-6 if loaded ~111 ~ ~-6 if loaded ~141 ~ ~-6 if loaded ~171 ~ ~-6 if loaded ~201 ~ ~-6 if loaded ~231 ~ ~-6 if loaded ~261 ~ ~-6 run return run function railworks:gallery/far_away/far
schedule function railworks:gallery/far_away/wait 1t
