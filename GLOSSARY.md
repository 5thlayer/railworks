# Railworks

Trains: Locomotives and Cargo cars riding on top of a raised Rail, carrying players, mobs and items between Stations, driven by hand or running jobs between hatches. It is Factorio's train game as a straddle-beam monorail, kept apart from vanilla rails, and it plays alone in vanilla on Groundworks.

## Language

The Module's terms, each with what it is and the words to avoid. `/domain-modeling` adds them as they are resolved.

### Parties

**Railworks**:
This Module.
_Avoid_: Library, Binding

**FactoryWorks**:
The suite of 5thlayer mods this Module belongs to, with the other Modules.
_Avoid_: the -works suite, the Pack

**Consumer**:
A mod that builds against this Module, such as another FactoryWorks Module, or a modpack that loads it.
_Avoid_: client (collides with the game's client side), dependent, integration

### Track

**Rail**:
The beam a Train rides on top of, straddling it, strung Span by Span over Pylons. Also the item that strings it, spent by a Span's length.
_Avoid_: track, monorail, girder, beam

**Pylon**:
The column that holds the Rail up, stacked segment by segment to the height the player chooses. Its top segment carries one Arm.
_Avoid_: pillar, support, pole (Wireworks'), post, Γ Pylon (when the Arm stuck out sideways)

**Arm**:
The top of a Pylon, where the Rail rests and its Spans meet. It takes two Spans at most, so a Line never branches.
_Avoid_: bracket, node, attachment point

**Span**:
The Rail between two Arms. It curves through each Arm, climbs and bends only within its limits, and keeps the space above it clear. One Train holds a Span at a time.
_Avoid_: segment (a Pylon's), section, block (Factorio's rail block), link

**Line**:
Spans joined Arm to Arm that a Train can run along without leaving the Rail. A line's end is an Arm with one Span.
_Avoid_: route, track

**Clearance**:
The space above a Span, and down both sides of the Rail, that a car straddling it passes through, which no block may fill.
_Avoid_: loading gauge, hitbox

### Trains

**Train**:
Cars coupled on the Rail that move as one.
_Avoid_: monorail, consist

**Car**:
One of the Locomotives, Cargo cars and Fluid cars a Train is made of, coupled to the car ahead and the car behind. It faces the Train's way or the other, so a Train can have a Locomotive facing out at each end. Placing one beside an end of a Train at rest couples it there; shears uncouple it.
_Avoid_: wagon, carriage, vehicle, rolling stock

**Locomotive**:
A car that pulls its Train forward. The Burner Locomotive burns fuel, up to four stacks of it, put in by hand or by a provider beside it at a Station; the Electric Locomotive runs on a charge of FE, put in only by a Charger beside it at a Station. Each pulls only while it holds fuel or charge. Each is three blocks long, with a cab for its driver and a bay behind for one rider. Locomotives stand only at a Train's ends, alone or several together: nothing but Locomotives lies between one and an end.
_Avoid_: engine (Wireworks' steam engine), loco

**Driver**:
The player in a Locomotive's cab, who drives a manual Train. Only a player drives.
_Avoid_: passenger, operator, conductor

**Rider**:
Whoever rides a Locomotive but does not drive it, in the bay behind the cab: one mob, or a second player.
_Avoid_: passenger (for every entity mounted), mob rider, seat 1

**Cargo car**:
A car that carries items, loaded and unloaded by the hatch beside its car spot while its automatic Train stands at a Station on a job, or by hand through what it holds, which using it opens. Nobody rides one.
_Avoid_: wagon, freight car

**Fluid car**:
A car that carries one fluid in its tank, filled and emptied by the Fluid hatch beside its car spot while its automatic Train stands at a Station on a job, or by hand a bucket at a time. Nobody rides one.
_Avoid_: tank car, tanker, fluid wagon

**View**:
A car's entity in a loaded chunk, showing where its Train puts it and what it holds. The Train, the car and its cargo and fuel are the level's; the view only shows them, and a car out of view has none.
_Avoid_: car entity (for the car itself)

**Abstract Train**:
A Train with none of its cars in view, run by the level on the same tick and the same Spans as every other Train. It keeps its cargo, fuel, job, claims and riders, and its views reappear where it stands once its chunks load.
_Avoid_: ghost train, virtual train, unloaded train

**Straight leg**:
Rail without a bend, square to the blocks: Spans each in line through both their Arms, or ending there, running along x or z. A Cargo car loads only on one, and a Station is placed only by one (ADR 0007).
_Avoid_: straight track

**Station**:
A named block beside the Rail, by a Straight leg, serving the Trains that have it on their drive side and letting the others pass. A Train stops with its leading car at it, its cars on its platform (ADRs 0007, 0008). Several may share a name, which is only shown.
_Avoid_: stop (Voidworks' Stop), train stop, depot

**Drive side**:
The side of the Rail a Station stands on to serve a Train, seen the way the Train runs: the right, unless the server sets the left.
_Avoid_: handedness, traffic side

**Platform**:
The Straight leg behind a Station where a Train stopped there stands: its leading car's place at the Station, and up to four car spots behind it; no two Stations' platforms overlap.
_Avoid_: siding, bay, loading area

**Car spot**:
The two blocks of a platform where a stopped car behind the leading car stands, counted back from the leading car; the first is the place of the car coupled to it. The leading car, usually a Locomotive, has no car spot, but a hatch may stand beside its place as beside a car spot.
_Avoid_: slot (an inventory's), position, bay

**Automatic**:
A Train taking jobs, as against one driven by hand, which is manual.
_Avoid_: autopilot, scheduled, unattended

**Status line**:
What a Train is doing, as a player reads it in its Locomotive's menu and in Jade: manual, waiting for a job, heading for or at a Station to load or unload, unable to reach one, or holding cargo with nowhere to deliver. When it heads to unload or has nowhere to deliver, the line also shows the items and fluids it carries.
_Avoid_: state, status text, train info

### Logistics

**Goods**:
What automatic Trains carry between hatches, of two kinds: items, held by a hatch of items and a Cargo car, and fluid, held by a Fluid hatch and a Fluid car. Each hatch and car holds one kind and trades only with the other side of its kind.
_Avoid_: freight, wares (for the domain; `Ware` is only the code's name for one good, an item's kind or a fluid)

**Hatch**:
A container beside a Station's platform at a car's floor, joining that Station, which serves the car standing at the leading car's place or the car spot it is beside: a hatch of items trades with a Cargo car, and its provider fuels a Locomotive; a **Fluid hatch** trades with a Fluid car. It is either a provider or a requester; one beside no platform joins nothing and is only a container.
_Avoid_: Well hatch (Voidworks'), port, loader, inserter, hopper

**Fluid hatch**:
A hatch holding one fluid in a tank, filled and emptied by any mod's pipes, which trades only with a Fluid car, as a hatch of items trades only with a Cargo car. A provider Fluid hatch fuels no Locomotive.
_Avoid_: tank, fluid port, fluid loader

**Charger**:
A block beside a Station's platform at a car's floor, joining that Station as a hatch does, which takes FE from any mod's cable and charges the Electric Locomotive standing beside it, of any Train at rest there, manual or automatic. It holds no items and neither offers nor asks. The Creative Charger, for creative play and tests, never runs out and fills a Locomotive at once.
_Avoid_: charging hatch, charging station, battery

**Burner Generator**:
A block that burns furnace fuel into FE and gives it to the energy blocks touching it, so a Charger runs in a world with no other energy mod. It powers only what touches it.
_Avoid_: generator (any mod's), boiler, steam engine (Wireworks')

**Provider**:
A hatch offering what is put in it to requesters at the same car spot of other Stations, and fuelling the Locomotive of an automatic Train beside it from what no job claims; it never asks.
_Avoid_: source, output hatch, giver

**Requester**:
A hatch asking for one item, so many stacks of it, or a Fluid hatch asking for one fluid, so many buckets of it: automatic Trains deliver it there until it holds that many, counting the loads already on their way, and it never gives.
_Avoid_: sink, input hatch, receiver

**Job**:
An automatic Train's errand: from a provider Station to a requester Station, with one load for each car spot where the requester asks for what the provider offers, each claimed against its two hatches as it is planned. A delivery job has no provider, and carries what the Train holds already.
_Avoid_: Trip (Voidworks'), order, route, schedule, task

**Leftover**:
What a Cargo car or a Fluid car holds that no job of its Train claims, left after a requester stopped wanting it or a job was given up as cut off from its requester, or put in by hand; its Train takes only delivery jobs until it is gone.
_Avoid_: stowed load, surplus

### Dev runs

**Game-test pack**:
A datapack only this Module's game tests switch on, holding the data a Consumer would supply, such as a tag's entries or a recipe, so a test can stand on it. Never in what players download.
_Avoid_: test datapack, gametest datapack

**Dev pack**:
A datapack only this Module's dev client switches on, holding the data a Consumer would supply, so a feature a Consumer turns on with data can be tried by hand. Never in what players download.
_Avoid_: debug pack, test pack

### Shipped packs

**Gallery pack**:
A datapack in this Module's jar, off until a player turns it on, whose functions build a working scene of each of the Module's features, each with a sign naming it. Functions only: turning it on changes nothing about how the Module plays (libworks ADR 0010).
_Avoid_: demo pack, showcase pack, gallery datapack
