# Project conventions

## Scope and stack

Bulgarian Trainer is a single-user local application for a Russian-speaking learner.
Use Java 21, the existing stable Spring Boot version, Maven, Spring Web MVC,
Spring Data JPA, persistent file-mode H2, HTML5, CSS3, and vanilla JavaScript ES modules.
Use JUnit 5 and Spring Boot Test. Keep dependencies minimal and preserve the existing
Initializr project and Maven wrapper; change the POM only when necessary.

Do not use React, Angular, Vue, Bootstrap, Docker, microservices, or authentication.
The initial milestone stops at setup, health checking, and the homepage. Do not add
vocabulary features, imports, training modes, spaced repetition, or statistics without
a subsequent request.

## Architecture

Base package: `com.example.bulgariantrainer`.
Use `controller -> service -> repository -> database` for domain operations.

- `controller`: HTTP mappings, status codes, request/response DTOs; no business logic.
- `service`: business rules and transaction boundaries; constructor dependency injection.
- `repository`: Spring Data JPA persistence access; no direct controller access.
- `entity`: persistent domain objects; do not serialize entities as API contracts.
- `dto`: API request and response models; prefer records for simple data carriers.
- `exception`: application exceptions and centralized REST error handling when needed.

Keep code understandable; avoid unnecessary interfaces, abstractions, and placeholder
features. The health check is a liveness endpoint and does not need a repository.
Serve frontend files from `src/main/resources/static`; use same-origin JSON REST APIs.
Keep frontend state and error handling explicit, accessible, and small.

## Language and local data

Use UTF-8 everywhere and support Russian and Bulgarian Cyrillic.
All visible frontend text must be Russian, including status and error messages.
Use English for identifiers, comments, and documentation.
Bind to loopback by default. Store H2 under `./data/bulgariantrainer`, relative to the
project root. Never delete user vocabulary files, commit generated database files,
or run tests against the personal database. Do not add automatic data imports.

## Verification

For changes to behavior, add meaningful JUnit 5 or Spring Boot tests at the appropriate
layer. Use constructor injection in application code; Spring test fixture field injection
is acceptable. Activate the `test` profile for database integration tests so main
configuration is retained while database settings use isolated in-memory H2.

Before delivering changes:

1. Run `./mvnw clean verify` (Windows: `.\mvnw.cmd clean verify`) with JDK 21.
2. Start the application and verify affected REST endpoints over HTTP.
3. For frontend changes, check the page in a browser, including failure/retry behavior
   when connectivity changes; verify Cyrillic, keyboard access, and narrow layouts.
4. Update README instructions when setup, API behavior, or configuration changes.
5. Report verification results and any limitations honestly.

Do not add tests that merely mirror implementation or unnecessary libraries. Preserve
useful existing files, inspect current code before editing, and stay within requested scope.
