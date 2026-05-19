# Easy Language Learning

Local-first Spring Boot application for vocabulary practice with two game modes:

- Flashcards for quick repetition.
- Match game with per-session and optional per-user scoring.

## Overview

Easy Language Learning uses CSV files instead of a database:

- Word data is loaded from a configurable source.
- Score history is persisted in a configurable CSV file.

At runtime, the app keeps a health snapshot of loaded data. If data is invalid, gameplay is disabled and users can reload data without restarting the app.

> [!IMPORTANT]
> This README documents the currently implemented behavior in source code.

## Features

- Two modes: Flashcards and Match.
- Home page with optional nickname and mode selection.
- HTMX-powered partial updates for card refresh, board updates, and health banner reloads.
- Alpine.js powered flashcard flip interaction.
- Native HTML5 drag-and-drop in match mode.
- Runtime CSV reload endpoint for degraded-data recovery.
- CSV-backed score tracking with FIFO history (last 10 S/F values per user-word pair).

## Tech Stack

| Layer | Technology |
| --- | --- |
| Runtime | Java 26 |
| Backend | Spring Boot 4.0.6, Spring MVC, Thymeleaf |
| Frontend interactivity | HTMX, Alpine.js |
| CSV | Apache Commons CSV |
| Build | Gradle Kotlin DSL |
| Tests | JUnit 5, Spring Boot Test, MockMvc, AssertJ, Mockito |

## Getting Started

### Prerequisites

- Java 26
- Gradle wrapper (already included)

### Run the app

Windows:

```bat
gradlew.bat bootRun
```

Linux/macOS:

```bash
./gradlew bootRun
```

Then open http://localhost:8080.

### Run tests

Windows:

```bat
gradlew.bat test
```

Linux/macOS:

```bash
./gradlew test
```

## Gameplay Behavior

### Flashcards

- Requires an active flashcards session.
- Shows one random word card at a time.
- Click card to reveal translation and optional example.
- Next card is loaded via HTMX partial.

### Match

- Requires an active match session.
- Board is generated from random word pairs.
- Correct match marks a pair as solved; when board is fully solved, a new board is generated.
- Every attempt is stored as S or F for optional end-of-session persistence.
- Session completion is success-based: 30 successful matches.

### Result page

- Displays successes, failures, and success rate.
- Message mapping:
	- 100% -> You did it!
	- 85-99% -> Almost!
	- Below 85% -> Let's practice some more!

## Configuration

Configuration values in application properties:

| Property | Default | Description |
| --- | --- | --- |
| `spring.application.name` | `easyll` | Spring app name |
| `app.scores.file-path` | `./scores.csv` | Score CSV output path |
| `app.words.source` | `classpath:data/dictionary.csv` | Word CSV source (classpath or file path) |

## Data Files

### Word CSV format

Semicolon-delimited, first row is treated as metadata:

```text
FROM_LANG;TO_LANG;EXAMPLE
Letter;Betu;
Stone;Ko;
```

Validation rules:

- Exactly 3 columns per row.
- FROM and TO values cannot be empty.
- EXAMPLE can be empty.
- Duplicate (FROM, TO) pairs are rejected.

### Score CSV format

Semicolon-delimited, no header:

```text
USER;FROM;TO;HISTORY
alice;Letter;Betu;S,F,S
```

Rules:

- HISTORY contains only S/F tokens separated by commas.
- Blank history values are invalid.
- History is capped to the last 10 entries per (user, from, to).
- Missing score file is treated as empty history.

> [!NOTE]
> Score writes are done at match-session end and flushed to CSV using an atomic replace strategy.

## Data Health and Reload

On startup and on reload, both CSV sources are parsed and validated.

- Healthy state: gameplay is enabled.
- Degraded state: errors are shown, game start is disabled.

Endpoints:

- `GET /health/data` - diagnostics page.
- `POST /admin/data/reload` - reload CSV data (supports HTMX fragment response).

## HTTP Endpoints

Pages:

- `GET /`
- `GET /flashcards`
- `GET /match`
- `GET /match/result`
- `GET /health/data`

Actions:

- `POST /session/start`
- `GET /flashcards/card`
- `POST /match/attempt`
- `POST /admin/data/reload`

## Project Structure

```text
src/main/java/com/yodawife/easyll/
	controller/
	domain/
	repository/
	service/
	validation/

src/main/resources/
	application.properties
	data/
	static/css/
	templates/

src/test/java/com/yodawife/easyll/
```

## Known Constraints

- Sessions are stored in memory (restart clears active sessions).
- No authentication (nickname-only identity for score tracking).
- Default word source is bundled classpath data.