# P1 reliability batch closure

Date: 2026-10-06 (+08:00).

## Accepted scope

The maintenance acceptance closes these audit items:

- R1: atomic Account ID and Player UID allocation.
- R2: database-writer shutdown drain.
- R3: World/Scene lock-order inversion.
- H1: Dispatch RPC request/response correlation.
- A-01: reliquary-exchange input validation.
- A-02: combine quantity bounds, overflow checks, and duplicate-cost aggregation.
- H2: handbook action work limits.
- BP-01: resource-compiler path safety.
- BP-13: CI test-resource directory preparation.

Root `data/` content auditing and cleanup remain owned by separate work. This
batch does not change that data or deployment configuration.

## Acceptance and integration provenance

- Accepted source: `fix/p1-final-integration` at
  `1faacb8e82c0a26e5d49dc2eb6326044423d140b`.
- Accepted source tree: `e4d2a7f459460035bec09453d717d960dab88fab`.
- That tree is identical to the final-review source at
  `0ec12fbf6e36bb184b9f5dd4a4ea373dd801f722`.
- [Build #1092](https://github.com/RinoPaw/AstaPS/actions/runs/37358980892)
  succeeded at `1bbb985bc8aedc8c3f1e4b66d53bece1c3296558`. The difference from
  this CI commit to the accepted source is limited to restoring `build.yml`;
  no temporary CI-trigger or format-investigation changes remain in the
  accepted runtime code.
- The acceptance handoff reports an independent final review with no blocker,
  Java 21 `clean jar test`, resource-compiler Python tests, whitespace checks,
  and Genshin Impact 7.1 Global/Windows live-client validation passing.
- Inherited Spotless formatting debt was classified separately. It is not a
  claim that repository-wide formatting is clean.
- Integration target: `play/rino` at
  `9704daaa6c3d1616dc5824463eb4df7760aeab6e`.
- The target's five quest source/test changes and the source's 32 P1 changes
  are disjoint. The automatic merge preserves both sets byte-for-byte; the
  only additional tracked file is this closure record.

The combined tree is revalidated with the project Java 21 toolchain, all Java
tests, the resource-compiler Python tests, and whitespace checks before the
local `play/rino` branch is advanced. The validation record in the maintenance
chat distinguishes this integrated-tree validation from the live-client
acceptance already supplied for the P1 source.

## Follow-up: combine payment and award must be atomic

Status: **SHOULD FIX**, deliberately outside the accepted P1 batch.

`CombineManger.combineItem()` validates the quantity and aggregated costs,
then successfully calls `Inventory.payItems()`. Its later `Inventory.addItem()`
can fail because output resources are invalid or the applicable inventory
capacity is exhausted. The current method does not restore the paid inputs;
it also does not use the output-addition result to determine success. The
reliquary-exchange award path likewise cannot restore already-consumed inputs
when output insertion fails.

Resolve this in a dedicated inventory-transaction change:

1. Validate every output definition and capacity before consuming inputs.
2. Define one transaction boundary for input consumption, output insertion,
   persistence, and the client result. A preflight check alone is insufficient
   if a later insertion can still fail or state changes concurrently.
3. Propagate failed output insertion and restore inputs or use an equivalent
   atomic commit mechanism; never report success without the award.
4. Preserve the accepted quantity bounds, checked arithmetic, aggregated
   costs, and reliquary input validation.

Regression acceptance: force output insertion to fail after an otherwise valid
payment and assert unchanged currency/materials/equipment, no success result,
and the same state after reload. Cover missing output resources, full capacity,
concurrent consumption, and a normal successful combine/exchange.
