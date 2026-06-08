# Easy Language Learning

A local-first Spring Boot application for vocabulary practice with two interactive game modes: **Flashcards** and **Match**.

Phase 3 of the DB migration blueprint is complete as of 2026-06-08 (PostgreSQL cutover complete, migration executed, and DB profile now default).

[User Guide](docs/user-guide.md) &bull; [Developer Guide](docs/developer-guide.md) &bull; [Ops Checklist](docs/ops-checklist.md)

---

## Features

- **Flashcards** — flip cards to reveal translations and usage examples.
- **Match** — drag-and-drop word pairs across two language columns; both directions supported.
- **Dictionary Management** — browse, search, sort, paginate, and toggle word availability per mode.
- **Account/player system** — choose or create a player from the account menu, switch players, or sign out without leaving the app.
- **Multi-language dictionaries** — language bundles loaded from `data/dictionaries/{languageCode}`.
- **Globally unique word IDs** — dictionary `WORD_ID` values are UUIDs used as stable score references across files.
- **Eligibility-aware gameplay** — Flashcards and Match only use words enabled for the selected mode.
- **Per-session scoring** — live success/failure counter throughout the match game.
- **Per-user scoring** — signed-in history persisted through the active profile adapter (last 12 attempts per word pair).
- **Repository interface boundaries** — score read/write and dictionary read contracts are active (`ScoreReadRepository`, `ScoreWriteRepository`, `DictionaryRepository`).
- **Profile-gated persistence adapters** — `db` profile (default) uses PostgreSQL adapters, `csv` profile remains available for fallback/import workflows.
- **Dedicated CSV dictionary adapter** — `CsvDictionaryRepository` delegates to `DataHealthService`; dictionary repository ownership is no longer on `DataHealthService`.
- **Flyway schema management** — baseline migration at `src/main/resources/db/migration/V1__init.sql`.
- **CSV to DB migration runner** — `CsvToDbMigrationRunner` runs when `app.migration.enabled=true` and supports dry-run mode.
- **Startup pairId integrity validation** — warns when a score `pairId` no longer exists in dictionary data.
- **PostgreSQL-first default** — application runs on PostgreSQL by default via `spring.profiles.active=db`.
- **CSV fallback/import utility** — `csv` profile remains available for compatibility and migration support.
- **Spring Boot 4 Flyway module gotcha** — Flyway auto-configuration requires explicit `org.springframework.boot:spring-boot-flyway` dependency.
- **Runtime data reload** — pick up CSV changes without restarting the app.
- **Data health page** — see parse errors and disable gameplay automatically when data is invalid.

## Quick Start

**Prerequisites:**

- Java 26
- PostgreSQL 16+ running locally (default profile is `db`)

If PostgreSQL is not available yet, see [System Setup](#system-setup-postgresql-default-runtime) below.

```sh
# Windows
gradlew.bat bootRun

# Linux / macOS
./gradlew bootRun
```

Open [http://localhost:8080](http://localhost:8080).

## System Setup (PostgreSQL default runtime)

The application now starts in DB mode by default:

```properties
spring.profiles.active=db
```

### 1) Install PostgreSQL

Use PostgreSQL 16+ and ensure the server is running on port `5432`.

Windows example:

```powershell
winget install PostgreSQL.PostgreSQL.16
```

### 2) Create database and user

Run in `psql` as a superuser:

```sql
CREATE DATABASE easyll;
CREATE USER easyll WITH PASSWORD 'easyll';
GRANT ALL PRIVILEGES ON DATABASE easyll TO easyll;
```

### 3) Validate connection properties

Defaults are in `src/main/resources/application-db.properties`:

- `spring.datasource.url=jdbc:postgresql://localhost:5432/easyll`
- `spring.datasource.username=easyll`
- `spring.datasource.password=easyll`

Override with environment variables when needed:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

### 4) Start the app

```sh
# Windows
gradlew.bat bootRun

# Linux / macOS
./gradlew bootRun
```

Flyway runs automatically in DB profile and applies migrations from `src/main/resources/db/migration`.

### Optional: run without PostgreSQL (CSV fallback)

```sh
# Windows
gradlew.bat bootRun --args='--spring.profiles.active=csv'

# Linux / macOS
./gradlew bootRun --args='--spring.profiles.active=csv'
```

## Guides

| Guide | Audience |
| --- | --- |
| [User Guide](docs/user-guide.md) | Playing the app — modes, scoring, custom word lists |
| [Developer Guide](docs/developer-guide.md) | Building, configuring, testing, extending |
| [Ops Checklist](docs/ops-checklist.md) | Fresh machine setup, startup verification, and troubleshooting |
| [DB Migration Blueprint](docs/architecture/db-migration-blueprint.md) | Incremental migration plan, target schema, rollout phases |
| [ADR 0001](docs/adr/0001-db-migration-readiness.md) | Decision record for CSV-now, DB-ready architecture |

## Tech Stack

| Layer | Technology |
| --- | --- |
| Runtime | Java 26 |
| Backend | Spring Boot 4.0.6, Spring MVC, Thymeleaf |
| Frontend | HTMX 2.0, Alpine.js 3.14 |
| CSV | Apache Commons CSV |
| Security | Spring Security (HTTP Basic for admin endpoints) |
| Static analysis | Error Prone + NullAway (JSpecify mode) |
| Build | Gradle Kotlin DSL |
| Tests | JUnit 5, Spring Boot Test, MockMvc, AssertJ, Mockito |

## Data Layout

The application uses filesystem-backed multi-language dictionaries:

```text
data/
	dictionaries/
		hun/
			words.csv
			mode-eligibility.csv
		pl/
			words.csv
			mode-eligibility.csv
```

### words.csv

```text
WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
90eadc73-ef0e-3efe-a5f3-e2ecd0b76d28;Letter;Betű;Írtam egy betűt a barátomnak.;true
```

`WORD_ID` is a UUID string, and `mode-eligibility.csv` plus score records reference the same stable ID.

### mode-eligibility.csv

```text
WORD_ID;MODE;ENABLED
w1;flashcards;true
w1;match;false
```

Eligibility rule used by games:

- A word is playable only when `GLOBAL_ENABLED=true` and mode-specific eligibility is enabled.
- Missing `WORD_ID + MODE` entry defaults to enabled.

## Configuration

Key properties in `src/main/resources/application.properties`:

```properties
app.dictionaries.root-path=./data/dictionaries
app.dictionaries.primary-language-code=hun
app.dictionaries.modes=flashcards,match
app.scores.file-path=./data/scores/scores.csv
app.scores.write-path=./data/scores/scores.csv
app.accounts.file-path=./data/users/users.csv
spring.profiles.active=db
spring.profiles.group.test=csv
app.migration.enabled=false
app.migration.dry-run=true
app.migration.errors-output-path=./data/migration-errors.csv
```

DB cutover default in `src/main/resources/application.properties`:

```properties
spring.profiles.active=db
```

DB profile overrides in `src/main/resources/application-db.properties`:

- `spring.datasource.*` for PostgreSQL connection
- `spring.flyway.enabled=true`
- `spring.flyway.locations=classpath:db/migration`

## Testing status

- Latest Phase 2 completion run: 227 passing, 16 skipped (Testcontainers without Docker), 0 failing.
- DB parity tests use Testcontainers PostgreSQL and skip gracefully when Docker is not available.
- Phase 3 smoke verification (2026-06-08): app starts cleanly on `db`, Flyway reports schema up to date, and startup pairId integrity validation passes.

## Phase 3 completion snapshot (2026-06-08)

1. Local PostgreSQL 16 setup validated (`winget install PostgreSQL.PostgreSQL.16`), with database/user credentials: `easyll` / `easyll`.
2. Flyway baseline migration `V1__init.sql` applied and created: `app_user`, `dictionary_pair`, `score_attempt`, `score_progress`.
3. CSV-to-DB migration executed successfully: 2 users, 207 dictionary pairs, 116 score entries, 0 errors.
4. Runtime default switched to DB (`spring.profiles.active=db`).
5. CSV profile retained as fallback/import utility.

## Migration runner operation

`CsvToDbMigrationRunner` is a one-shot migration utility. Keep `app.migration.enabled=false` for normal runtime after migration is complete.

## Known Constraints

- Sessions are held in memory — restarting the server clears all active sessions.
- Gameplay account selection has no password-based authentication.
- Score history is capped to the last 12 attempts per word pair per user.
- Dictionary editing writes to files under `data/dictionaries`; ensure the process has write permission.