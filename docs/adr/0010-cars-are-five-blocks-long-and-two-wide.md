---
status: accepted
---

> Amends [ADR 0007](0007-a-station-is-a-block-beside-or-under-the-rail.md), on where a Station stands beside the Rail, and [ADR 0008](0008-trains-take-jobs-from-hatches-not-schedules.md), on which hatch serves a Locomotive.

# Cars are five blocks long and two wide

#62 made the Locomotive three blocks long and left the Cargo car at two, and a Train of the two looked uneven. Factorio gives its locomotive and its wagons one length, and its stops a spacing that fits every car. So every car, Locomotive, Cargo car and Fluid car, is five blocks long coupler to coupler and two wide, its body 0.2 short of its length, and a car spot is a car's five blocks, so a platform is a Locomotive's place and four spots, 25 blocks. The Locomotive is a cab at its front, a closed Engine room over its middle block, and a two-block bay at its back for one Rider; the Cargo car draws its chests over its three inner blocks. Nothing had been released, so no save is migrated.

Two wide is the costly width. The Rail runs along the middle of a row of blocks, so a two-wide car centred on it reaches halfway into the blocks either side: the Clearance is three blocks across, as a three-wide car's would be, and the car's sides stand half a block short of the next blocks out. Two was picked over three for how the cars look, and over one for the room it gives the bay and the chests. So Stations, hatches and Chargers stand in one row two blocks off the Rail, at the height of a Cargo car's chests rather than at a car's floor, replacing ADR 0007's Station within 1.25 blocks of the Rail's line; the half block of air between a hatch and its car is crossed, while a car stands at the hatch's spot, by a hatch of items' Bellows or a Fluid hatch's Loading arm, drawn and not a block. Only a provider or a Charger beside a Locomotive's Engine room fuels or charges it, wherever along the platform it stands, so the four other blocks beside a Locomotive serve only a Cargo or Fluid car stopped there. Railworks sets no rule for where a player stands to reach a hatch.

The prototype that settled the shape, a viewer of each length and width tried, is [`prototype/car-length/index.html`](../../prototype/car-length/index.html), opened in a browser; variant H is the one chosen.
