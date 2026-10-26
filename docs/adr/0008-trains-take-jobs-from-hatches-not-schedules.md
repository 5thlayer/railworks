---
status: accepted
---

# Trains take jobs from hatches, not Schedules

#26 first ran a Train unattended on a Schedule, a list of Stations each with when to leave, as Factorio's automatic mode does, and #25 loaded its Cargo cars with vanilla hoppers set in spots beside and under the stopped car. Both left the player wiring every Train to every outpost by hand and building hoppers in the air beside a raised Rail. Railworks instead takes the logistics of Factorio's requester and provider chests, as Voidworks' Matter line does with its Well hatches (Voidworks ADR 0011): a **hatch** beside a Station's platform is a provider or a requester, and an **automatic** Train has no list of its own but takes **jobs**, each from a provider Station to a requester Station, planned by the server and claimed against the hatches as they are planned, so several Trains share the work without overshooting a request. The nearest free automatic Train that can reach the provider along its Line, the way its fuelled Locomotives face, and then the requester takes a job; one with no job waits where it last unloaded, since there is no depot.

A hatch trades only with the Cargo car standing beside it, as a Factorio inserter loads its own wagon, so a Train is a row of car spots two blocks apart (the car's pitch made exactly two), its leading car at the Station, and one Train carries a different item in each car to the requesters at matching spots. A job pairs every car spot of the two Stations where the requester asks for what the provider offers. To make that row well defined, a Station serves only the Trains that have it on their drive side, right unless the server sets left, as a Factorio train stop serves only trains with it on their right, and its platform runs back from it along the Straight leg, four cars at most, overlapping no other Station's. A Station under the Rail has no side and is no longer placed. Hatches trade only with an automatic Train on a job at their Station, a stack a second each, so a Train driven by hand is never emptied in passing and the claims stay true; a player fills a manual Train through its Cargo cars as before.

A hatch keeps its contents in the level's saved data beside the Stations, and its block is a view onto that record, so an abstract Train (ADR 0004) loads and unloads with no chunk loaded, while hoppers and pipes reach the block once it is loaded. The Cargo car no longer opens its sides to hoppers or gives an item capability: the hatch is the only way in or out unattended.

## Considered options

- **Keep the Schedule beside the hatches**: two ways to run a Train unattended, each with its own menu and its own rules at a Station, where jobs alone cover what a Schedule did with no wiring.
- **Trade with every Cargo car of a stopped Train**, as ADR 0007 first let every car load wherever it stood: dispatch would need no geometry, but a Train could carry only one item per job, and no mixed Train would be possible.
- **Hoppers into the Cargo car**, as #25 built: they need nothing new, but they work only with chunks loaded, and set beside a raised Rail they stand in the air.
- **Stations facing the way they are placed**: the side of the Rail already says it in Factorio's terms, with nothing to set or misread.
- **One Station on each side of a stretch, one for each direction**: hatches by both platforms would need a rule for which Station they join, so platforms never overlap and a line served both ways has its Stations apart.
