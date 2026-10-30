# Railworks

<!-- For players, written by the maintainer and never by an agent: what Railworks is, why it exists
and how it feels to play. It is also the Modrinth and CurseForge description
(docs/agents/publishing.md). The technical sections go below it. -->

Factorio's trains as a straddle-beam monorail.

## Status

In design: the first release is specified in [5thlayer/factoryworks#705](https://github.com/5thlayer/factoryworks/issues/705). Nothing is playable yet.

## Dependencies

Minecraft 26.1.2, NeoForge and Groundworks, which Railworks requires for its placement preview and does not bundle. The libworks runtime is nested in the jar.

## Gallery

Railworks carries a gallery of its features: Pylons and Rail, the grade and bend limits, curves, the Burner Locomotive, Cargo cars coupled into a Train, Stations, and automatic Trains carrying goods from provider hatches to requesters, each a scene with a sign naming it, built one behind another going north of where you stand. It is a datapack in the jar, **Railworks Gallery**, off until you turn it on in a world's Data Packs screen or with `/datapack enable "mod/railworks:gallery_pack"`. Then `/function railworks:gallery/build` builds every scene, and `/function railworks:gallery/<scene>` one of them, such as `coupling`. Click a scene's sign to build it again from the start. A function needs cheats in single player (on at world creation, or Open to LAN with cheats) or operator rights on a server. The gallery clears the ground it builds on and changes nothing else; it adds no recipes, loot or worldgen.

## Art

The Station's, the Rail's and the Pylon's sprites are by Malcolm Riley ([unused-textures](https://github.com/malcolmriley/unused-textures)), under CC BY 4.0: his small stone tiles, with the Station's arrow painted on, his terminal and his concrete paver, his grey cement, concrete slabs and steel block. `scripts/build-textures.py` builds them from his unmodified files in `data/art/`, and `NOTICE` and `REUSE.toml` record each file.
