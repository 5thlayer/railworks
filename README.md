![Railworks](https://raw.githubusercontent.com/5thlayer/railworks/main/publish/railworks-cover.png)

<!-- For players, written by the maintainer and never by an agent: what Railworks is, why it exists
and how it feels to play. It is also the Modrinth and CurseForge description
(docs/agents/publishing.md). The technical sections go below it. -->

Railworks adds trains running on a monorail. A train is built by stringing a rail between pylons, setting a locomotive on it and coupling cargo and fluid cars behind it. Automatic trains can transport items and fluids from a station that provides them to one that requests them. Players, and a pet or any other mob, can also be transported by riding on the locomotive and setting it on manual drive.

## Why?

Because it was the missing piece of my -works suite: intra-dimensional long haul logistics to complement short range belts and inter-dimensional void dragons.

**NeoForge, Minecraft 26.1.2 only.** Please report anything odd on [GitHub](https://github.com/5thlayer/railworks/issues).

## Features

*   **A raised line.** Stack a **Pylon** to the height you want, then string the **Rail** from one Pylon's Arm to the next with the Rail in hand, paying by length. The Rail curves smoothly through each Pylon, and a Train rides on top of it, straddling the beam, so a line runs above your base and over other lines at any height. A preview shows each Span before you string it, green where it would go and red where it wouldn't; cutting a Span or breaking a Pylon gives the Rail back.
*   **Lines that ride right.** A Span too long, too steep or bending too sharply is refused, so a climb over another line takes a run-up and nothing makes a hairpin. The space a Train passes through stays clear: no Span is strung through blocks, and no block is placed in a Span's way. A moving Train strikes whoever stands on the line.
*   **Trains you build.** Set a **Burner Locomotive** on the Rail and couple **Cargo cars** and **Fluid cars** behind it by placing them; shears part a Train again. A Cargo car holds a double chest, a Fluid car 160 buckets of one fluid. Every car adds weight and every Locomotive pull, so a long Train starts and stops slowly, and an automatic Train that must run both ways needs a Locomotive facing out at each end. Ride in the cab and drive it yourself, with a friend or a mob on a lead in the seat behind you.
*   **Two Locomotives.** The Burner burns any furnace fuel; the **Electric Locomotive** runs on FE, from a **Charger** beside it at a Station, which any mod's cable feeds. With no energy mod, a **Burner Generator** burns fuel into FE for the Charger touching it.
*   **Stations and hatches.** Place a **Station** beside a straight stretch of Rail, and **hatches** beside where its cars stop: a provider offers what any mod's hoppers and pipes put in it, and a requester asks for an item or a fluid. An automatic Train takes jobs on its own, from provider to requester, and is refuelled by a provider beside its Locomotive. Its status shows in the Locomotive's menu and in Jade.
*   **Trains far away.** An automatic Train keeps running its jobs through chunks nobody has loaded, loading and unloading as it would in view, and shows up where it stands when you come back. One Train holds a Span at a time, so several share a line safely with no signals to place.
*   **A gallery.** The Railworks Gallery datapack in the jar builds a working scene of each feature (see Gallery).
*   **Plays alone in vanilla.** Railworks needs no other mod but Groundworks; in the -works suite it carries goods and fluids between your outposts and your base.

## Dependencies

Minecraft 26.1.2, NeoForge and [Groundworks](https://github.com/5thlayer/groundworks), which Railworks requires for its placement preview and does not bundle. The libworks runtime is nested in the jar.

## Gallery

Railworks carries a gallery of its features: Pylons and Rail, the grade and bend limits, curves, the Burner Locomotive, Cargo cars coupled into a Train, Stations, and automatic Trains carrying goods from provider hatches to requesters, each a scene with a sign naming it, built one behind another going north of where you stand. It is a datapack in the jar, **Railworks Gallery**, off until you turn it on in a world's Data Packs screen or with `/datapack enable "mod/railworks:gallery_pack"`. Then `/function railworks:gallery/build` builds every scene, and `/function railworks:gallery/<scene>` one of them, such as `coupling`. Click a scene's sign to build it again from the start. A function needs cheats in single player (on at world creation, or Open to LAN with cheats) or operator rights on a server. The gallery clears the ground it builds on and changes nothing else; it adds no recipes, loot or worldgen.

## Art

The block sprites are by Malcolm Riley ([unused-textures](https://github.com/malcolmriley/unused-textures)), under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/): the Station's small stone tiles, with its arrow painted on, terminal and concrete paver; the Rail's and the Pylon's grey cement, concrete slabs and steel block; the cars' steel plating, stripes and copper tank, and the Cargo car's crates; the hatches' duct, access-hatch plating and copper tank, tinted for their mode; the Chargers' copper coil and battery, the Creative Charger's re-lit pink; and the Burner Generator's smelter and refractory brick. The Burner Locomotive's firebox doors are the smelter's front, and the Electric's grilles the Charger's coil. The cars' and the Rail's item icons are painted from the same sprites. `scripts/build-textures.py` builds them from his unmodified files in `data/art/`, and `NOTICE` and `REUSE.toml` record each file.

The cover and icon are a row of Pylons carrying the Rail and a Train riding on it, drawn by `publish/make-cover.py` from those same sprites, so they are his too and under CC BY 4.0, until an in-game screenshot or an artist's art replaces them.

## Developing

A 5thlayer Module, released on its own to Modrinth and CurseForge. The design is [5thlayer/factoryworks#705](https://github.com/5thlayer/factoryworks/issues/705). See `GLOSSARY.md` for the domain glossary. `sh ./gradlew build` runs the JUnit tests on a plain JVM, and `sh ./gradlew runGameTestServer` the game tests headless. Releases go through the libworks plugin's `release` task (`docs/agents/releases.md`).

## License

MIT, except the block sprites, the item icons, the cover and the icon, which are Malcolm Riley's under CC BY 4.0 (see Art). Source is on [GitHub](https://github.com/5thlayer/railworks).

This is a hobby project: I’m not monetizing any part of FactoryWorks. I’m building it for fun, to see how far I can take it.
