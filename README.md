# Easy Language Learning

A local-first Spring Boot application for vocabulary practice with two interactive game modes: **Flashcards** and **Match**.

[User Guide](docs/user-guide.md) &bull; [Developer Guide](docs/developer-guide.md)

---

## Features

- **Flashcards** — flip cards to reveal translations and usage examples.
- **Match** — drag-and-drop word pairs across two language columns; both directions supported.
- **Per-session scoring** — live success/failure counter throughout the match game.
- **Per-user scoring** — optional nickname-based history persisted to CSV (last 10 attempts per word pair).
- **CSV-backed word data** — bring your own word list; no database required.
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

## Known Constraints

- Sessions are held in memory — restarting the server clears all active sessions.
- No login or authentication for gameplay (nickname-only identity).
- Score history is capped to the last 10 attempts per word pair per user.
- Default word source is the bundled classpath file; set `app.words.source` in `application.properties` to use a custom file.