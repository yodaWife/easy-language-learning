# Easy Language Learning

A Spring Boot application for vocabulary practice with Flashcards and Match game modes.

CSV-to-PostgreSQL migration is complete. The application now runs on PostgreSQL only.

[User Guide](docs/user-guide.md) | [Developer Guide](docs/developer-guide.md) | [Ops Checklist](docs/ops-checklist.md)

## Features

- Flashcards mode for quick vocabulary practice
- Match mode with per-attempt success/failure flow
- Dictionary management (search, sort, pagination, global and per-mode toggles)
- Guest and signed-in account flows
- Per-user score history and progress tracking
- Flyway-managed schema migrations

## Quick Start

Prerequisites:

- Java 26
- PostgreSQL 16+

Create database and user:

```sql
CREATE DATABASE easyll;
CREATE USER easyll WITH PASSWORD 'easyll';
GRANT ALL PRIVILEGES ON DATABASE easyll TO easyll;
```

Run the app:

```sh
# Windows
gradlew.bat bootRun

# Linux / macOS
./gradlew bootRun
```

Open http://localhost:8080.

## Configuration

Main runtime configuration is in `src/main/resources/application.properties`.

Key defaults:

- `spring.profiles.active=db`
- `spring.profiles.group.test=db`
- `spring.datasource.url=jdbc:postgresql://localhost:5432/easyll`
- `spring.datasource.username=easyll`
- `spring.datasource.password=easyll`
- `spring.flyway.enabled=true`
- `spring.flyway.locations=classpath:db/migration`

Environment overrides:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## Migrations

Flyway scripts:

- `src/main/resources/db/migration/V1__init.sql`
- `src/main/resources/db/migration/V2__mode_eligibility.sql`

`V2__mode_eligibility.sql` adds table `mode_eligibility` with a foreign key to `dictionary_pair`.

## Testing

Run tests:

```sh
./gradlew test
```

Controller integration tests use PostgreSQL Testcontainers through `AbstractControllerIntegrationTest`.

## Constraints

- Sessions are in-memory and reset on application restart.
- Account selection is not password-based user authentication.
- Score history stores the last 12 attempts per `(userId, pairId, mode)`.