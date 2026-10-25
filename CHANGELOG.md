# Changelog

Written for Consumers: what a mod building against Railworks can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased
- Pylons and Rail: a Pylon crafts from iron and stacks into a column, one Pylon a segment, its Arm on the top segment. Using Rail on one Arm and then another strings a Span costing `ceil(length)` Rail, up to 64 blocks, at most two to an Arm. Repeating it cuts the Span; breaking a Pylon cuts its Spans, and either refunds the Rail. Spans are saved with the level and a client is sent those in the chunks it loads.
