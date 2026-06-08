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

## Implementation update (2026-06-08)

Phase 3 implementation is complete.

Implemented boundaries in code:

1. `ScoreReadRepository` (`getHistoriesForUser`, `knownUsers`).
2. `ScoreWriteRepository` (`appendAttempt`, `flush`).
3. `DictionaryRepository` (`findLanguage`, `availableLanguages`).
4. `ScoreRepository` implements both score interfaces.
5. `CsvDictionaryRepository` implements `DictionaryRepository` by delegating to `DataHealthService` snapshots.
6. `ScoreProgressService` depends on `ScoreReadRepository`.
7. `MatchGameApplicationService` depends on `ScoreWriteRepository`.

Phase 2 delivery now implemented:

1. Profile-gated adapter strategy with `db` (default) and `csv` fallback/import profile.
2. PostgreSQL adapters for account, dictionary, score read, and score write repositories.
3. Flyway schema migration at `src/main/resources/db/migration/V1__init.sql`.
4. One-off startup migrator `CsvToDbMigrationRunner` enabled via `app.migration.enabled=true` and supporting dry-run.
5. Migration error output via `MigrationErrorRecorder` to configurable CSV path.
6. DB adapter parity/contract tests running with Testcontainers (graceful skip when Docker unavailable).

Phase 3 cutover now implemented:

1. Runtime default switched to PostgreSQL by setting `spring.profiles.active=db`.
2. Flyway migration `V1__init.sql` executed and schema validated as up to date on subsequent starts.
3. Live CSV-to-DB migration completed via `CsvToDbMigrationRunner` (2 users, 207 dictionary pairs, 116 score entries, 0 errors).
4. `csv` profile retained as fallback/import utility path.
5. Migration runner treated as one-shot tooling; `app.migration.enabled=false` remains the steady-state setting.
6. Spring Boot 4.x Flyway module gotcha resolved by explicit `org.springframework.boot:spring-boot-flyway` dependency.

Additional readiness guard implemented:

1. `PairIdIntegrityValidator` runs on `ApplicationReadyEvent` and logs WARN for score `pairId` values not present in the dictionary.

Deliberate naming deviation from the original blueprint:

1. The blueprint used `ScoreAttemptRepository` and `ScoreProgressRepository` with `ScoreAttempt`/`ScoreProgress` domain types.
2. The implemented code uses `ScoreReadRepository` and `ScoreWriteRepository` because those domain objects do not exist yet and are planned for phase 2 DB modeling.
3. This keeps phase 1 CSV delivery pragmatic while preserving dependency inversion for adapter swap.

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
4. Startup referential-integrity checks now detect orphan score `pairId` values early.
5. Runtime cutover has been executed through profile switch (`csv` -> `db`) without controller/service rewiring.

### Negative

1. Additional short-term complexity from abstraction layers.
2. Temporary duplicate logic risk if CSV and DB adapters diverge without shared tests.

## Non-goals (for this iteration)

1. No redesign beyond completed DB runtime cutover and migration.
2. No user authentication redesign beyond current session-based account selection.
3. No distributed session infrastructure.

## Quality gates

Before DB cutover:

1. Parity tests pass against both CSV and DB implementations.
2. Migration dry-run report is generated and reviewed for unresolved mappings.
3. Data health endpoint validates both dictionary and score stores.

Current verification snapshot for completed Phase 2 test run:

1. 227 passing
2. 16 skipped (Testcontainers when Docker unavailable)
3. 0 failing

Phase 3 status (2026-06-08):

1. Environment/production migration and DB profile cutover are complete.
2. CSV adapters remain available for fallback/import path only.

## References

- [docs/architecture/db-migration-blueprint.md](../architecture/db-migration-blueprint.md)
- [docs/developer-guide.md](../developer-guide.md)
