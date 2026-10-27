---
status: accepted
---

# A segment that would raise a Span past a rule is refused

Stacking a Pylon segment on an Arm raises the Arm, and its Spans with it. A raised Span can come to stretch past the length limit, climb past the grade limit, bend past the bend limit at either end, or run its Clearance through a block, and the segment itself can stand in another Span's Clearance. Railworks refuses that segment, the Placement Preview red and the action bar saying which rule it would break, rather than placing it and cutting the Span that broke. Every other way a Span comes to be (a click, a placed Pylon, `/railworks span`) refuses what breaks a rule and spends nothing, and a refusal shows before the click where a cut shows only after it, as a Span gone and Rail on the ground. What it gives up is the column as a quick way to cut a Span: the player cuts it with the Rail first. A segment that reaches the column some way the item never plans, such as `/setblock`, cannot be refused, so where it raises a Span past a rule it cuts the Arm's Spans and drops their Rail, as losing the Arm would. A segment so set in another Span's Clearance stays, as any block a command sets does. The rule is `Stringing.raise`.
