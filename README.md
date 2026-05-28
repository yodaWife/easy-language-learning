# Easy Language Learning

A local-first Spring Boot application for vocabulary practice with two interactive game modes: **Flashcards** and **Match**.

[User Guide](docs/user-guide.md) &bull; [Developer Guide](docs/developer-guide.md)

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
- **Per-user scoring** — signed-in history persisted to CSV (last 12 attempts per word pair).
- **CSV-backed data** — no database required.
- **Runtime data reload** — pick up CSV changes without restarting the app.
- **Data health page** — see parse errors and disable gameplay automatically when data is invalid.

## Quick Start

**Prerequisite:** Java 26

```sh
# Windows
gradlew.bat bootRun

# Linux / macOS
./gradlew bootRun
```

Open [http://localhost:8080](http://localhost:8080).

## Guides

| Guide | Audience |
| --- | --- |
| [User Guide](docs/user-guide.md) | Playing the app — modes, scoring, custom word lists |
| [Developer Guide](docs/developer-guide.md) | Building, configuring, testing, extending |
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
```

## Known Constraints

- Sessions are held in memory — restarting the server clears all active sessions.
- Gameplay account selection has no password-based authentication.
- Score history is capped to the last 12 attempts per word pair per user.
- Dictionary editing writes to files under `data/dictionaries`; ensure the process has write permission.