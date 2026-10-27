---
status: accepted
---

# Each Span is a block, and there are no signals

> Amended in #5: Junctions are deferred, and T Pylons dropped. An Arm takes two Spans, so every Line is a chain or a closed loop, and a Train stays on the Line it was placed on. A network is built as spokes instead: a Line of its own from each outpost to where its goods go, one Train shuttling on it, the Lines flying over each other (ADR 0002) to arrive at different heights. With one Train to a Line nothing contends for a Span, so Junctions' cost, which is ways through an Arm, holding the Arm, never stopping inside a Junction, pathfinding, steering, Train limits and the deadlocks players would have to design around, buys nothing yet. Two ordinary Pylons side by side carry two parallel lines as a T Pylon would. Junctions come back if players outgrow the spokes; Span holding stays as written, so signals still wouldn't be needed then.

Factorio keeps its trains apart with signals the player places, which divide the track into blocks. Railworks makes the division implicit: each Span is a block that one Train holds at a time, and a Train holds the Spans it occupies plus the next one ahead before it moves onto it, so it always has room to brake. There are no signal items and no switch blocks; a Junction is just an Arm with three or more Spans, and any two of them within the bend limit are a way through it. The player gets collision-free Trains by building Pylons, and gives up the finer control of placing signals mid-Span: a long Span is held by one Train however long it is, which the length limit bounds. The design is 5thlayer/railworks#1.
