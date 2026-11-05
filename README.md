# HandsOn

A volunteer shift scheduler for small nonprofits. Coordinators post events split into
shifts; volunteers sign up, cancel, or join a waitlist; coordinators track attendance and
hours.

Work in progress. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design.

## Tech stack

Java 21, Spring Boot 4, Spring Data JPA, PostgreSQL 18, Flyway, JUnit 5, Testcontainers.
Frontend (coming later): TypeScript, HTML, CSS.

## Running locally

You need Java 21 and Docker. Maven is not required; the project includes the Maven Wrapper.

1. Create your local settings file, then open it and set your own passwords:

   ```bash
   cp .env.example .env
   ```

   `HANDSON_ADMIN_EMAIL` and `HANDSON_ADMIN_PASSWORD` create the first coordinator account
   when the app starts. Public sign-up only creates volunteer accounts.

2. Start PostgreSQL:

   ```bash
   docker compose up -d
   ```

3. Start the app (Flyway creates the tables on first start):

   ```bash
   ./mvnw spring-boot:run
   ```

   On Windows PowerShell use `.\mvnw.cmd spring-boot:run`.

## Running tests

Docker must be running. The tests start their own temporary PostgreSQL container.

```bash
./mvnw test
```
