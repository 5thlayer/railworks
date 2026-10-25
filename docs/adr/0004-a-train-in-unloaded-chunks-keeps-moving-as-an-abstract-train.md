---
status: accepted
---

# A Train in unloaded chunks keeps moving as an abstract Train

A Train whose chunks unload becomes an abstract Train: its cars, cargo, fuel and Schedule are held by the rail network model, which advances it along the stored Spans on the same tick it advances every other Train. When its chunks load again it reappears as entities where the model says it is. Trains never force-load chunks, so a network costs a server no chunk-loading. This works only because a Train never checks for blocks while it moves (only a loaded Train meets players and mobs): a Span's Clearance is guaranteed when the Span is strung, and a block placed into it later is refused, so the model needs no blocks to know the way is free. The alternatives were freezing Trains outside loaded chunks, which stops a distant outpost line, and chunk-loading their path, which costs the server. The design is 5thlayer/railworks#1.
