# Database Migration Blueprint (Dictionary + Scoring)

## 1. Goal

Deliver current account/scoring requirements on CSV now, while shaping the code and data model for near-term migration to a relational database.

### Implementation sync status (2026-06-08)

Phase 3 is complete in the codebase.

Completed in implementation:

1. Account/session controller and service.
2. `ActiveUserContext` in `HttpSession`.
3. Repository interfaces and CSV adapters.
4. Score key `(userId, pairId, mode)`.
5. History cap increased from 10 to 12.
6. Dictionary progress column visible only to signed-in users.
7. Startup `pairId` referential-integrity validator.

Additional phase 2 delivery completed:

1. Flyway schema migration at `src/main/resources/db/migration/V1__init.sql`.
2. PostgreSQL adapters: `PostgresAccountRepository`, `PostgresScoreReadRepository`, `PostgresScoreWriteRepository`, `PostgresDictionaryRepository`.
3. Profile gating strategy via `PersistenceProfiles`: `db` (default) and `csv` fallback/import profile.
4. `CsvDictionaryRepository` introduced; `DataHealthService` no longer implements `DictionaryRepository`.
5. Startup CSV-to-DB migrator `CsvToDbMigrationRunner` behind `app.migration.enabled` with dry-run support.
6. Testcontainers parity tests for DB adapters (gracefully skipped when Docker is unavailable).

Phase 3 cutover delivery completed:

1. Runtime default switched from `csv` to `db` in `application.properties`.
2. Flyway executed `V1__init.sql`; schema contains `app_user`, `dictionary_pair`, `score_attempt`, `score_progress`.
3. Live CSV-to-DB migration completed via `CsvToDbMigrationRunner`: 2 users, 207 dictionary pairs, 116 score entries, 0 errors.
4. Startup smoke verification passed on `db` profile (Flyway reports schema up to date and pairId integrity check passes).
5. CSV profile retained as fallback/import utility.
6. Spring Boot 4.x Flyway autoconfiguration gotcha documented: explicit `org.springframework.boot:spring-boot-flyway` dependency is required.

Actual code has precedence over this plan where names differ.

## 2. Scope and assumptions

In scope:

1. Signed-in vs anonymous account state in session.
2. Sign-in/switch/sign-out user flow from main menu.
3. Session continuity across page reloads.
4. Score history window increased to last 12 attempts.
5. Dictionary progress column visible only to signed-in users.
6. Stable pair identity for all score references.

Assumptions:

1. Browser session (`HttpSession`) remains the active session mechanism.
2. Existing dictionary `wordId` can be reused as `pair_id` if immutable and globally unique.

## 3. Persistence contracts (implemented in phase 1)

Create interfaces first. Keep CSV implementations active for now.

```java
public interface AccountRepository {
    Optional<Account> findById(String userId);
    Optional<Account> findByDisplayName(String displayName);
    List<Account> findAllActive();
    Account save(Account account);
}

public interface ScoreReadRepository {
  Map<String, List<String>> getHistoriesForUser(String userId);
  Set<String> knownUsers();
}

public interface ScoreWriteRepository {
  void appendAttempt(String userId, String pairId, String mode, String result);
  void flush();
}

public interface DictionaryRepository {
    Optional<LanguageBundle> findLanguage(String languageCode);
    List<String> availableLanguages();
    // Existing write operations can stay in DictionaryEditService initially,
    // then move behind this interface in phase 2.
}
```

Implementation notes:

1. `ScoreRepository` implements both `ScoreReadRepository` and `ScoreWriteRepository`.
2. `CsvDictionaryRepository` implements `DictionaryRepository` by delegating to `DataHealthService` snapshot reads.
3. `ScoreProgressService` depends on `ScoreReadRepository` (not concrete `ScoreRepository`).
4. `MatchGameApplicationService` depends on `ScoreWriteRepository` (not concrete `ScoreRepository`).
5. Naming deviation from the original blueprint is deliberate: `ScoreAttemptRepository`/`ScoreProgressRepository` were replaced by `ScoreReadRepository`/`ScoreWriteRepository` because `ScoreAttempt` and `ScoreProgress` domain objects are planned for phase 2 DB modeling and do not yet exist in the CSV-era model.

## 4. Domain model updates

### 4.1 Active user context

Store in `HttpSession` separately from game session:

```java
public record ActiveUserContext(@Nullable String userId,
                                @Nullable String displayName,
                                boolean signedIn) {}
```

### 4.2 Score key and rolling window

Canonical key:

1. `user_id`
2. `pair_id`
3. `mode`

Rules:

1. Accept only `S` and `F` result symbols.
2. Keep FIFO last 12 attempts.
3. Anonymous users are never persisted.

## 5. Target relational schema

```sql
create table app_user (
  user_id        varchar(36) primary key,
  display_name   varchar(64) not null,
  created_at_utc timestamp not null,
  active         boolean not null default true,
  constraint uq_app_user_display_name unique (display_name)
);

create table dictionary_pair (
  pair_id            varchar(64) primary key,
  language_code      varchar(16) not null,
  from_word          varchar(200) not null,
  to_word            varchar(200) not null,
  example            varchar(500) not null,
  global_enabled     boolean not null,
  created_at_utc     timestamp not null,
  updated_at_utc     timestamp not null
);

create table score_attempt (
  attempt_id         bigint generated always as identity primary key,
  user_id            varchar(36) not null references app_user(user_id),
  pair_id            varchar(64) not null references dictionary_pair(pair_id),
  mode               varchar(32) not null,
  result             char(1) not null check (result in ('S', 'F')),
  attempted_at_utc   timestamp not null
);

create index ix_attempt_user_pair_mode_time
  on score_attempt(user_id, pair_id, mode, attempted_at_utc desc);

create table score_progress (
  user_id            varchar(36) not null references app_user(user_id),
  pair_id            varchar(64) not null references dictionary_pair(pair_id),
  mode               varchar(32) not null,
  history_last12     varchar(32) not null,
  success_count      smallint not null,
  total_count        smallint not null,
  success_percent    smallint not null,
  updated_at_utc     timestamp not null,
  primary key (user_id, pair_id, mode)
);
```

Notes:

1. If keeping dictionary CSV in phase 1, `dictionary_pair` can be introduced in DB during phase 2 only.
2. If `wordId` is already stable and globally unique, use it directly as `pair_id` value.

## 6. API and UI behavior changes (current requirements)

Endpoints:

1. `GET /account/panel` - users and active status.
2. `POST /account/sign-in` - select existing or create new user.
3. `POST /account/sign-out` - clear active user context.
4. `GET /account/status` - signed-in/anonymous indicator for main menu.

Dictionary view:

1. If `signedIn == true`, show progress column.
2. If `signedIn == false`, hide progress column.

## 7. Migration from CSV runtime state (implemented in phase 2)

Runtime trigger:

1. `CsvToDbMigrationRunner` executes on startup only when `app.migration.enabled=true`.
2. `app.migration.dry-run=true` logs planned writes without mutating DB.

Algorithm implemented in code:

1. Load users from `CsvAccountRepository` and insert/upsert into `app_user`.
2. Load dictionary bundles from `DataHealthService` snapshot and insert into `dictionary_pair`.
3. Load score histories from snapshot (`ScoreKey -> UserWordHistory`).
4. Validate `userId` exists and `pairId` exists in migrated dictionary set.
5. For each score key, write `score_attempt` rows (synthetic chronological timestamps) for the last 12 entries.
6. Upsert `score_progress` with `history_last12`, counts, and success percentage.
7. Record invalid rows through `MigrationErrorRecorder` to `app.migration.errors-output-path`.

## 8. Incremental implementation plan

### Phase 1: Feature delivery with migration-ready design

1. Add account/session controller and service.
2. Add `ActiveUserContext` in `HttpSession`.
3. Add repository interfaces and CSV adapters.
4. Change scoring key to `(userId,pairId,mode)`.
5. Increase history cap from 10 to 12.
6. Add dictionary progress column for signed-in user only.

Status: completed (2026-06-01).

Delivered in this phase beyond original list:

1. `PairIdIntegrityValidator` (`@Component`) runs on `ApplicationReadyEvent` and logs WARN for score `pairId` values that are missing from dictionary data.
2. Dictionary `row-new` fragment `colspan` now respects `progressEnabled`, so add-row rendering stays aligned for both signed-in and anonymous views.

### Phase 2: DB adapter introduction

Status: completed (2026-06-08).

Delivered:

1. Flyway migration V1 schema.
2. JDBC PostgreSQL adapters for account, dictionary, score read, and score write repositories.
3. Contract/parity test coverage for DB adapters via Testcontainers.
4. One-off migration runner (`CsvToDbMigrationRunner`) with dry-run and error reporting.

### Phase 3: Cutover

Status: completed (2026-06-08).

Delivered:

1. Cutover to `db` profile completed.
2. Production migration run completed with 0 recorded migration errors.
3. CSV adapters retained for fallback/import utility workflows.
4. Post-cutover operation set to `app.migration.enabled=false` (one-shot migration runner remains disabled during normal runtime).

## 9. Test strategy

1. Unit tests for rolling last-12 logic and progress percentage.
2. Controller tests for sign-in/switch/sign-out flow and main-menu indicator.
3. Integration tests for dictionary progress visibility by account state.
4. Adapter contract tests shared by CSV and DB implementations.
5. Migration test fixtures including ambiguous/unresolvable legacy rows.
6. Testcontainers-based DB tests skip gracefully when Docker is unavailable.

## 10. Risks and mitigations

1. Risk: key mismatch between dictionary and scoring stores.
- Mitigation: startup referential integrity check for all `pair_id` values.

Implementation status: mitigation is active via `PairIdIntegrityValidator`.

2. Risk: account display-name collisions.
- Mitigation: case-insensitive uniqueness policy and deterministic normalization.

3. Risk: CSV and DB adapter behavior drift.
- Mitigation: same contract test suite required for both adapters.

## 11. Definition of done for readiness

1. No persistence logic outside repository adapters.
2. Controllers/services compile unchanged when adapter switches from CSV to DB.
3. Stable `user_id` and `pair_id` used in all new score writes.
4. Migration dry-run report available with explicit unresolved rows.

Phase 2 readiness status: items 1-4 are implemented in the current codebase.

Phase 3 status: all cutover items are implemented in the current codebase and validated in runtime smoke checks.
