---
status: accepted
---

# Each Span is a block, and there are no signals

Factorio keeps its trains apart with signals the player places, which divide the track into blocks. Railworks makes the division implicit: each Span is a block that one Train holds at a time, and a Train holds the Spans it occupies plus the next one ahead before it moves onto it, so it always has room to brake. There are no signal items and no switch blocks; a Junction is just an Arm with three or more Spans, and any two of them within the bend limit are a way through it. The player gets collision-free Trains by building Pylons, and gives up the finer control of placing signals mid-Span: a long Span is held by one Train however long it is, which the length limit bounds. The design is 5thlayer/railworks#1.
