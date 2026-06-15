# Operations Checklist

This checklist is for local environment setup, first run verification, and basic recovery operations.

## 1. Preflight

- OS: Windows, Linux, or macOS
- Java 26 available in PATH
- PostgreSQL 16+ installed and running
- Port `5432` available for PostgreSQL
- Port `8080` available for app runtime

Verify tool versions:

```sh
java --version
```

Windows (PowerShell) PostgreSQL client check:

```powershell
psql --version
```

## 2. PostgreSQL Bootstrap

Connect as a PostgreSQL superuser and run:

```sql
CREATE DATABASE easyll;
CREATE USER easyll WITH PASSWORD 'easyll';
GRANT ALL PRIVILEGES ON DATABASE easyll TO easyll;
```

Optional quick connectivity test:

```sh
psql -h localhost -p 5432 -U easyll -d easyll -c "SELECT 1;"
```

## 3. App Configuration Baseline

Expected default runtime profile:

```properties
spring.profiles.active=db
```

Expected DB datasource values (can be overridden by environment variables):

- `spring.datasource.url=jdbc:postgresql://localhost:5432/easyll`
- `spring.datasource.username=easyll`
- `spring.datasource.password=easyll`

Environment override variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## 4. Start Application

Windows:

```powershell
./gradlew.bat clean bootRun
```

Linux / macOS:

```sh
./gradlew clean bootRun
```

Expected startup outcomes:

- Application starts on `http://localhost:8080`
- Flyway reports migration status for schema `public`
- No datasource authentication errors

## 5. Smoke Verification

1. Open home page: `http://localhost:8080`
2. Open dictionary page: `http://localhost:8080/dictionary`
3. Start Flashcards flow and fetch next card
4. Start Match flow and submit at least one attempt
5. Validate admin endpoint auth behavior:

```sh
curl -i -X POST http://localhost:8080/admin/data/reload
```

Expected: `401 Unauthorized` without credentials.

With credentials:

```sh
curl -i -u admin:admin -X POST http://localhost:8080/admin/data/reload
```

Expected: success status from reload endpoint.

## 6. Data Health Controls

- Use `/health/data` to verify word and score health.
- Use `POST /admin/data/reload` (with admin credentials) to refresh runtime state.
- On reload failures, expect degraded-state reporting and investigate DB connectivity/logs.

## 7. Quick Troubleshooting

- Error: relation does not exist (`app_user`, etc.)
  - Confirm app runs with `db` profile and Flyway is enabled in DB profile.
- Error: password authentication failed
  - Verify DB credentials and environment variable overrides.
- Error: Name for argument not specified
  - Ensure controller parameter annotations include explicit names and rebuild.
- Port already in use
  - Free port `8080` (app) or `5432` (PostgreSQL), then restart.

## 8. Routine Maintenance

- Rotate default admin credentials in non-local environments.
- Back up PostgreSQL database before major upgrades.
- Keep DB migration files in source control and immutable once applied.
- Run tests before upgrades:

```sh
./gradlew test
```
