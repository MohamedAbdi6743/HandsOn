# HandsOn

A volunteer shift scheduler for small nonprofits. Coordinators post events split into
shifts; volunteers sign up, cancel, or join a waitlist; coordinators track attendance and
hours.

Work in progress. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design.

## Tech stack

- **Backend:** Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Flyway
- **Database:** PostgreSQL 18 (in Docker for local development)
- **Frontend:** plain TypeScript, HTML and CSS, with no framework or bundler. `tsc` compiles
  `frontend/src` into `src/main/resources/static/js`, and Spring Boot serves the pages.
- **Tests:** JUnit 5, Mockito, MockMvc, Testcontainers

## Running locally

You need Java 21, Node.js (only to compile TypeScript) and Docker. Maven is not required;
the project includes the Maven Wrapper.

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

3. Compile the frontend:

   ```bash
   cd frontend
   npm install
   npm run build
   cd ..
   ```

   While editing TypeScript, `npm run watch` recompiles on every save.

4. Start the app (Flyway creates the tables on first start):

   ```bash
   ./mvnw spring-boot:run
   ```

   On Windows PowerShell use `.\mvnw.cmd spring-boot:run`.

5. Open http://localhost:8080 (or the `APP_PORT` you set in `.env`). Log in with the
   coordinator account from `.env`, or create a volunteer account from the login page.

## Running tests

Docker must be running. The tests start their own temporary PostgreSQL container.

```bash
./mvnw test
```
