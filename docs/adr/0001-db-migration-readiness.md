# ADR 0001: Database Migration Readiness and Completion

- Status: COMPLETED
- Date: 2026-05-28
- Deciders: Easy Language Learning team

## Context

The product requires near-term feature delivery in two areas:

1. Account and session UX: signed-in user selection, switching, sign-out, and session continuity across page reloads.
2. Scoring and progress: keep last 12 attempts, expose per-user progress in dictionary view, and stop using FROM/TO text as persistence key.

The migration objective was to move dictionary/account/score persistence from CSV-era infrastructure to PostgreSQL while preserving application behavior and interface boundaries.

## Decision

Introduce repository boundaries, migrate data model to PostgreSQL, and complete runtime cutover to DB-only operation.

This means:

1. Repository interfaces were introduced and adopted in services.
2. Score key was normalized to `(userId, pairId, mode)` with rolling window 12.
3. Flyway schema migrations were introduced and applied.
4. Runtime profile was cut over to `db`.
5. CSV migration and fallback infrastructure was subsequently removed.

## Implementation update (2026-06-15)

Migration and cleanup are complete.

Implemented boundaries in code:

1. `ScoreReadRepository` (`getHistoriesForUser`, `knownUsers`).
2. `ScoreWriteRepository` (`appendAttempt`, `flush`).
3. `DictionaryRepository` now supports full read/write dictionary operations (6 methods).
4. DB repository implementations are active runtime adapters.
5. CSV repositories and CSV migration utilities are removed.
6. `ScoreProgressService` depends on `ScoreReadRepository`.
7. `MatchGameApplicationService` depends on `ScoreWriteRepository`.

Schema and adapter delivery implemented:

1. PostgreSQL adapters for account, dictionary, score read, and score write repositories.
2. Flyway baseline migration `V1__init.sql`.
3. Flyway `V2__mode_eligibility.sql` adding `mode_eligibility` with FK to `dictionary_pair`.
4. Controller integration tests standardized on PostgreSQL Testcontainers base class (`AbstractControllerIntegrationTest`).

Phase 3 cutover now implemented:

1. Runtime default switched to PostgreSQL by setting `spring.profiles.active=db`.
2. Flyway migration `V1__init.sql` executed and schema validated as up to date on subsequent starts.
3. Runtime default remains `spring.profiles.active=db`.
4. `spring.profiles.group.test=db` is active for integration tests.
5. CSV code paths, profiles, and migration runner properties are removed from runtime documentation and code.

Additional readiness guard implemented:

1. `PairIdIntegrityValidator` runs on `ApplicationReadyEvent` and logs WARN for score `pairId` values not present in the dictionary.

Deliberate naming deviation from the original blueprint:

1. The blueprint used `ScoreAttemptRepository` and `ScoreProgressRepository` with `ScoreAttempt`/`ScoreProgress` domain types.
2. The implemented code uses `ScoreReadRepository` and `ScoreWriteRepository` because those domain objects do not exist yet and are planned for phase 2 DB modeling.
3. This keeps phase 1 CSV delivery pragmatic while preserving dependency inversion for adapter swap.

## Rationale

### Why this worked

1. Stable repository boundaries reduced coupling during migration.
2. Flyway made schema evolution explicit and repeatable.
3. Testcontainers-based integration tests validated DB behavior in controller flows.

## Consequences

### Positive

1. Faster and safer delivery of requested account/scoring features.
2. Reduced migration risk through explicit contracts and stable IDs.
3. Data model now includes mode eligibility as first-class relational state.
4. Startup referential-integrity checks now detect orphan score `pairId` values early.
5. Runtime cutover has been executed through profile switch (`csv` -> `db`) without controller/service rewiring.

### Negative

1. Additional short-term complexity from abstraction layers.
2. Ongoing schema evolution still requires migration discipline.

## Non-goals (for this iteration)

1. No redesign beyond completed DB runtime cutover and migration.
2. No user authentication redesign beyond current session-based account selection.
3. No distributed session infrastructure.

## Quality gates

Before DB cutover:

1. DB-backed tests pass and runtime smoke checks pass on default profile.
2. Data health endpoint validates dictionary and score stores through DB access.
3. PairId integrity checks remain active at startup.

Current verification snapshot for completed Phase 2 test run:

1. 227 passing
2. 16 skipped (Testcontainers when Docker unavailable)
3. 0 failing

Final status (2026-06-15):

1. Migration is complete.
2. CSV-era code and data sources are removed.
3. PostgreSQL is the only persistence backend for normal operation.

## References

- [docs/architecture/db-migration-blueprint.md](../architecture/db-migration-blueprint.md)
- [docs/developer-guide.md](../developer-guide.md)
