# Database Migration Blueprint (Dictionary + Scoring)

## 1. Goal

Document final migration outcome and current PostgreSQL-only architecture for dictionary and scoring persistence.

### Implementation sync status (2026-06-15)

Phase 3 is complete in the codebase.

Completed in implementation:

1. Account/session controller and service.
2. `ActiveUserContext` in `HttpSession`.
3. Repository interfaces are active and backed by PostgreSQL implementations.
4. Score key `(userId, pairId, mode)`.
5. History cap increased from 10 to 12.
6. Dictionary progress column visible only to signed-in users.
7. Startup `pairId` referential-integrity validator.

Additional delivery completed:

1. Flyway schema migration at `src/main/resources/db/migration/V1__init.sql`.
2. PostgreSQL adapters: `PostgresAccountRepository`, `PostgresScoreReadRepository`, `PostgresScoreWriteRepository`, `PostgresDictionaryRepository`.
3. Flyway migration `V2__mode_eligibility.sql` added `mode_eligibility` with FK to `dictionary_pair`.
4. `DictionaryRepository` expanded to read/write contract with 6 methods.
5. `DataHealthService` now performs DB-backed reload and resilient degraded-state fallback on DB failures.
6. Controller integration tests use `AbstractControllerIntegrationTest` with PostgreSQL Testcontainers.

Phase 3 cutover delivery completed:

1. Runtime default switched from `csv` to `db` in `application.properties`.
2. Flyway executed `V1__init.sql`; schema contains `app_user`, `dictionary_pair`, `score_attempt`, `score_progress`.
3. Startup smoke verification passed on `db` profile (Flyway schema up to date and pairId integrity check passes).
4. CSV code, profiles, migration runner, and CSV data files were removed after migration completion.
5. Spring Boot 4.x Flyway autoconfiguration gotcha documented: explicit `org.springframework.boot:spring-boot-flyway` dependency is required.

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

Repository interfaces are now fully implemented and used with PostgreSQL adapters.

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
  void updateGlobalEnabled(String pairId, boolean enabled);
  void updateWordContent(String pairId, String fromWord, String toWord, String example);
  void insertWord(String languageCode, String pairId, String fromWord, String toWord, String example, boolean globalEnabled);
  void upsertModeEligibility(String pairId, String mode, boolean enabled);
}
```

Implementation notes:

1. `PostgresDictionaryRepository` implements dictionary read and write operations.
2. `ScoreProgressService` depends on `ScoreReadRepository`.
3. `MatchGameApplicationService` depends on `ScoreWriteRepository`.
4. Application services remain adapter-agnostic at interface level.

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

1. `mode_eligibility` table is part of applied schema via `V2__mode_eligibility.sql`.
2. `pair_id` remains the stable identifier for score and dictionary linking.

## 6. API and UI behavior changes (current requirements)

Endpoints:

1. `GET /account/panel` - users and active status.
2. `POST /account/sign-in` - select existing or create new user.
3. `POST /account/sign-out` - clear active user context.
4. `GET /account/status` - signed-in/anonymous indicator for main menu.

Dictionary view:

1. If `signedIn == true`, show progress column.
2. If `signedIn == false`, hide progress column.

## 7. Post-migration runtime state

Current operation:

1. Runtime uses PostgreSQL only (`spring.profiles.active=db`).
2. `DataHealthService` reads dictionaries from `DictionaryRepository` and validates score table access via JDBC.
3. Dictionary edits persist through repository writes to DB tables.
4. Integration tests execute against PostgreSQL Testcontainers via shared base class.

## 8. Incremental implementation plan

### Phase 1: Feature delivery with migration-ready design

1. Add account/session controller and service.
2. Add `ActiveUserContext` in `HttpSession`.
3. Add repository interfaces and migrate services to interface dependencies.
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
4. Flyway-based DB schema lifecycle established for runtime operation.

### Phase 3: Cutover and cleanup

Status: completed (2026-06-08).

Delivered:

1. Cutover to `db` profile completed.
2. Production migration run completed with 0 recorded migration errors.
3. CSV-era repositories, parsers, migration runner, and CSV data files removed.
4. Documentation and tests aligned to PostgreSQL-only architecture.

## 9. Test strategy

1. Unit tests for rolling last-12 logic and progress percentage.
2. Controller tests for sign-in/switch/sign-out flow and main-menu indicator.
3. Integration tests for dictionary progress visibility by account state.
4. Testcontainers-based DB tests skip gracefully when Docker is unavailable.
5. Controller integration tests share PostgreSQL setup via `AbstractControllerIntegrationTest`.

## 10. Risks and mitigations

1. Risk: key mismatch between dictionary and scoring stores.
- Mitigation: startup referential integrity check for all `pair_id` values.

Implementation status: mitigation is active via `PairIdIntegrityValidator`.

2. Risk: account display-name collisions.
- Mitigation: case-insensitive uniqueness policy and deterministic normalization.

3. Risk: schema drift across environments.
- Mitigation: strict Flyway migration discipline and startup validation.

## 11. Definition of done for readiness

1. No persistence logic outside repository adapters.
2. Controllers/services remain persistence-agnostic via repository interfaces.
3. Stable `user_id` and `pair_id` used in all new score writes.
4. Runtime and integration tests validate DB-only behavior end to end.

Phase 2 readiness status: items 1-4 are implemented in the current codebase.

Final status: migration and cleanup are complete; PostgreSQL is the normal and only persistence runtime.
