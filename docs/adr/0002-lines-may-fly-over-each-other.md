---
status: accepted
---

# Lines may fly over each other

Beltworks ADR 0003 (formerly FactoryWorks ADR-0044) keeps belts on the ground and makes crossing them a puzzle, arguing that free 3D routing removes the layout problem. Railworks departs from it on purpose: a Span may pass over or under any other Span at any height, so a crossing never makes a Train wait and needs no Junction. What keeps a network from being trivial is the cost a flyover carries rather than a ban: the grade limit means climbing over another line takes run-up distance, and every Span keeps its Clearance free. The design is 5thlayer/railworks#1.
