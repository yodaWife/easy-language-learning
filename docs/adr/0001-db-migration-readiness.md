# ADR 0001: Deliver Current Features First, With Database Migration Readiness

- Status: Accepted
- Date: 2026-05-28
- Deciders: Easy Language Learning team

## Context

The product requires near-term feature delivery in two areas:

1. Account and session UX: signed-in user selection, switching, sign-out, and session continuity across page reloads.
2. Scoring and progress: keep last 12 attempts, expose per-user progress in dictionary view, and stop using FROM/TO text as persistence key.

The application is currently CSV-backed and local-first. A full database migration is planned, but not yet mandatory for immediate feature release.

## Decision

Implement current requirements on the existing runtime, while introducing persistence boundaries that allow a low-risk migration to a relational database in the next milestone.

This means:

1. Keep CSV adapters as active persistence implementation now.
2. Introduce repository interfaces and storage-agnostic services now.
3. Normalize identifiers now:
   - `user_id` as immutable user identity.
   - `pair_id` (or existing immutable `wordId` if guaranteed globally unique and immutable) as dictionary pair identity.
4. Move score key to `(user_id, pair_id, mode)` and enforce rolling window = 12.
5. Prepare migration assets (schema, mapping rules, data migration script design) now.

## Rationale

### Why not migrate fully now

1. Current feature scope is broad and user-visible; adding a platform migration in the same cycle increases regression risk.
2. Data model requirements are still evolving (multi-language and multi-game scoring behavior), so immediate DB lock-in could require follow-up schema churn.

### Why readiness now

1. Most migration pain is caused by unstable identifiers and storage-coupled services.
2. Introducing interfaces and stable IDs now lets us swap persistence implementations with minimal controller/service rewiring.

## Consequences

### Positive

1. Faster and safer delivery of requested account/scoring features.
2. Reduced migration risk through explicit contracts and stable IDs.
3. Ability to support dual storage adapters during transition if needed.

### Negative

1. Additional short-term complexity from abstraction layers.
2. Temporary duplicate logic risk if CSV and DB adapters diverge without shared tests.

## Non-goals (for this iteration)

1. No immediate runtime switch to DB.
2. No user authentication redesign beyond current session-based account selection.
3. No distributed session infrastructure.

## Quality gates

Before DB cutover:

1. Parity tests pass against both CSV and DB implementations.
2. Migration dry-run reports zero unresolved `pair_id` mappings.
3. Data health endpoint validates both dictionary and score stores.

## References

- [docs/architecture/db-migration-blueprint.md](../architecture/db-migration-blueprint.md)
- [docs/developer-guide.md](../developer-guide.md)
