# Railworks

Trains: Locomotives and Cargo cars riding on top of a raised Rail, carrying players, mobs and items between Stations, by hand or on a schedule. It is Factorio's train game as a straddle-beam monorail, kept apart from vanilla rails, and it plays alone in vanilla on Groundworks.

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
The column that holds the Rail up, stacked segment by segment to the height the player chooses. Its top segment carries one Arm, or, on a T Pylon, a cross-head with two.
_Avoid_: pillar, support, pole (Wireworks'), post, Γ Pylon (when the Arm stuck out sideways)

**Arm**:
The top of a Pylon, where the Rail rests and its Spans meet. A T Pylon's two carry two lines side by side that never join.
_Avoid_: bracket, node, attachment point

**Span**:
The Rail between two Arms. It curves through each Arm, climbs and bends only within its limits, and keeps the space above it clear. One Train holds a Span at a time.
_Avoid_: segment (a Pylon's), section, block (Factorio's rail block), link

**Line**:
Spans joined Arm to Arm that a Train can run along without leaving the Rail. A line's end is an Arm with one Span.
_Avoid_: route (a Schedule's word), track

**Junction**:
An Arm with three or more Spans. Any two of them that bend gently enough are a way through it; nothing is switched.
_Avoid_: switch, points, fork

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
A car that carries items, loaded and unloaded only while its Train is stopped at a Station and it stands on a Straight leg: by a hopper beside it at its floor, set by sneak-using the hopper at the car, and by one under the Rail beneath it, the spots drawn while it is stopped there. Using it opens what it holds; nobody rides one.
_Avoid_: wagon, freight car

**Straight leg**:
Rail without a bend, square to the blocks: Spans each in line through both their Arms, or ending there, running along x or z. A Cargo car loads only on one, and a Station is placed only by one (ADR 0007).
_Avoid_: straight track

**Station**:
A named block beside the Rail or under it, by a Straight leg, serving the nearest Span in reach: a Train at rest with any car within a block either way of the point of the Span nearest it is at the Station, every car of it (ADR 0007). A Schedule names Stations, and several may share a name.
_Avoid_: stop (Voidworks' Stop), train stop, depot, platform

**Schedule**:
The list of Stations a Train runs through, each with when to leave.
_Avoid_: route, orders, timetable

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
