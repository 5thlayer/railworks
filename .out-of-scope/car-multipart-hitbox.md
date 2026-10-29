# A hit box along a car's whole body

A car is clicked, struck and looked at through one entity box, 2 by 2 at
its middle (`.sized(CarEntity.WIDTH, CarEntity.HEIGHT)` in
`RailworksRegistries`), though its body is 4.8 long. The last block and a
half at each end, most of a Locomotive's cab and its bay, can't be
clicked. Railworks keeps it that way.

## Why this is out of scope

Everything a player does with a car works from its middle: boarding as
the Driver or a Rider, leading a mob in, opening a Cargo car's menu,
shears, a bucket on a Fluid car, striking or breaking it, and Jade. Where
the click lands never changes what it does, so a longer box would only
let the same actions start from more places. In play, nobody has missed
that.

Making a car clickable along its length would take NeoForge `PartEntity`
parts, as the Ender Dragon has. Vanilla boxes are axis-aligned and can't
turn with a car on a bend, so the body would be covered by a row of
parts. Each part needs an id reserved before the car is added, a position
set every tick on both sides after the car's pose is worked out, and its
clicks and hits forwarded to the car. A rider would also have to be kept
from picking their own car's parts. That is a lot of moving machinery
for nothing a player can't already do.

If an action ever comes to depend on where along the car it is aimed,
choosing the bay over the cab for example, this is worth revisiting with
that action as its reason.

## Prior requests

- #64: "A multipart interaction box so a car can be clicked along its whole body"
