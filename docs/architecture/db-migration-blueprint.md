# Database Migration Blueprint (Dictionary + Scoring)

## 1. Goal

Deliver current account/scoring requirements on CSV now, while shaping the code and data model for near-term migration to a relational database.

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

## 3. Proposed persistence contracts

Create interfaces first. Keep CSV implementations active for now.

```java
public interface AccountRepository {
    Optional<Account> findById(String userId);
    Optional<Account> findByDisplayName(String displayName);
    List<Account> findAllActive();
    Account save(Account account);
}

public interface ScoreAttemptRepository {
    void append(ScoreAttempt attempt);
    List<ScoreAttempt> findByUserAndPairs(String userId, List<String> pairIds);
}

public interface ScoreProgressRepository {
    Optional<ScoreProgress> find(String userId, String pairId, String mode);
    Map<String, ScoreProgress> findByUserAndPairs(String userId, List<String> pairIds, String mode);
    void upsert(ScoreProgress progress);
}

public interface DictionaryRepository {
    Optional<LanguageBundle> findLanguage(String languageCode);
    List<String> availableLanguages();
    // Existing write operations can stay in DictionaryEditService initially,
    // then move behind this interface in phase 2.
}
```

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

1. `GET /account/menu-data` - users and active status.
2. `POST /account/sign-in` - select existing or create new user.
3. `POST /account/sign-out` - clear active user context.
4. `GET /account/status-fragment` - signed-in/anonymous indicator for main menu.

Dictionary view:

1. If `signedIn == true`, show progress column.
2. If `signedIn == false`, hide progress column.

## 7. Migration from existing scores.csv

Current shape: `user;fromWord;toWord;history`.

Migration algorithm:

1. Resolve `user` to `user_id` from `app_user` (create if missing).
2. Resolve `(fromWord,toWord,languageCode)` to `pair_id`.
3. Parse history symbols and trim to last 12.
4. Write generated attempts chronologically with synthetic timestamps if needed.
5. Recompute and upsert `score_progress`.
6. Log unresolved rows into `migration-errors.csv`.

Important:

1. If language cannot be inferred deterministically for legacy rows, migration must mark row unresolved and not silently guess.

## 8. Incremental implementation plan

### Phase 1: Feature delivery with migration-ready design

1. Add account/session controller and service.
2. Add `ActiveUserContext` in `HttpSession`.
3. Add repository interfaces and CSV adapters.
4. Change scoring key to `(userId,pairId,mode)`.
5. Increase history cap from 10 to 12.
6. Add dictionary progress column for signed-in user only.

### Phase 2: DB adapter introduction

1. Add Flyway migrations for target schema.
2. Implement JDBC/JPA adapters for repository interfaces.
3. Add parity tests to run against CSV and DB adapters.
4. Build one-off migration command.

### Phase 3: Cutover

1. Run dry-run migration in staging.
2. Run production migration with backups.
3. Switch active adapter to DB.
4. Keep CSV reader as fallback import utility only.

## 9. Test strategy

1. Unit tests for rolling last-12 logic and progress percentage.
2. Controller tests for sign-in/switch/sign-out flow and main-menu indicator.
3. Integration tests for dictionary progress visibility by account state.
4. Adapter contract tests shared by CSV and DB implementations.
5. Migration test fixtures including ambiguous/unresolvable legacy rows.

## 10. Risks and mitigations

1. Risk: key mismatch between dictionary and scoring stores.
- Mitigation: startup referential integrity check for all `pair_id` values.

2. Risk: account display-name collisions.
- Mitigation: case-insensitive uniqueness policy and deterministic normalization.

3. Risk: CSV and DB adapter behavior drift.
- Mitigation: same contract test suite required for both adapters.

## 11. Definition of done for readiness

1. No persistence logic outside repository adapters.
2. Controllers/services compile unchanged when adapter switches from CSV to DB.
3. Stable `user_id` and `pair_id` used in all new score writes.
4. Migration dry-run report available with explicit unresolved rows.
