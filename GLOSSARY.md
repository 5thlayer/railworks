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
One of the Locomotives and Cargo cars a Train is made of, coupled to the car ahead and the car behind. It faces the Train's way or the other, so a Train can have a Locomotive facing out at each end. Placing one beside an end of a Train at rest couples it there; shears uncouple it.
_Avoid_: wagon, carriage, vehicle, rolling stock

**Locomotive**:
A car that pulls its Train forward. The Burner Locomotive burns fuel; the Electric Locomotive runs on FE.
_Avoid_: engine (Wireworks' steam engine), loco

**Cargo car**:
A car that carries items, loaded and unloaded by the hatch beside its car spot while its automatic Train stands at a Station on a job, or by hand through what it holds, which using it opens. Nobody rides one.
_Avoid_: wagon, freight car

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
The two blocks of a platform where a stopped car behind the leading car stands, counted back from the leading car; the first is the place of the car coupled to it. The leading car, usually a Locomotive, has no car spot, so no hatch stands beside it.
_Avoid_: slot (an inventory's), position, bay

**Automatic**:
A Train taking jobs, as against one driven by hand, which is manual.
_Avoid_: autopilot, scheduled, unattended

### Logistics

**Hatch**:
A container beside a Station's platform at a car's floor, joining that Station, which trades with the Cargo car standing at its car spot. It is either a provider or a requester; one beside no platform joins nothing and is only a container.
_Avoid_: Well hatch (Voidworks'), port, loader, inserter, hopper

**Provider**:
A hatch offering what is put in it to requesters at the same car spot of other Stations; it never asks.
_Avoid_: source, output hatch, giver

**Requester**:
A hatch asking for one item, so many stacks of it: automatic Trains deliver it there until it holds that many, counting the loads already on their way, and it never gives.
_Avoid_: sink, input hatch, receiver

**Job**:
An automatic Train's errand: from a provider Station to a requester Station, with one load for each car spot where the requester asks for what the provider offers, each claimed against its two hatches as it is planned. A delivery job has no provider, and carries what the Train holds already.
_Avoid_: Trip (Voidworks'), order, route, schedule, task

**Leftover**:
What a Cargo car holds that no job of its Train claims, left after a requester stopped wanting it or a job was given up as cut off from its requester, or put in by hand; its Train takes only delivery jobs until it is gone.
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
