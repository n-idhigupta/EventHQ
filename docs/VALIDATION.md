# Validation results

Verified on 2026-11-02 (Asia/Calcutta), using Java 25 with Java 17 compilation target.

- `mvnw.cmd -B -ntp clean verify`: BUILD SUCCESS.
- 33 tests passed, zero failures, zero errors, zero skips.
- 12 H2 booking/security/catalog scenarios, 12 equivalent MySQL 8.4 Testcontainers scenarios, 7 HTTP provider scenarios, 1 pricing test, and 1 JWT validation test.
- Flyway migration and Hibernate validation passed on both databases.
- Packaged-JAR HTTP smoke passed: health, seeded catalog, registration/login, JWT, hold, payment, idempotent retry, ticket, protected admin servlet, and reactive upstream error.
- Docker Compose configuration validation passed.

Payments and notification delivery are simulated. HTTP integration tests use a local mock server. No live provider or cloud deployment was exercised.

## Startup-warning fix verification

- `run.cmd -B -ntp verify`: BUILD SUCCESS on installed Java 21; all 33 tests passed, including 12 MySQL scenarios.
- `run.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=18081`: startup passed using Java 21; `/actuator/health` returned UP.
- Removed the incubating Hibernate timestamp preference; explicit entity mappings preserve the existing schema and UTC JDBC configuration.
- Maven and application startup no longer print the reported Java 25 native-access/Unsafe warnings when using the launcher. Direct `mvnw.cmd` execution on Java 25 can still print third-party compatibility warnings.

## Web interface verification

- Maven verify passed all 35 tests with no failures, errors, or skips: 13 H2 scenarios, 13 MySQL scenarios, 7 HTTP integration tests, pricing, and JWT validation.
- Public welcome page/assets and protected ticket/admin routes are covered by regression tests.
- Chromium verified catalog search/filtering, account creation, live VIP/standard seat selection and exact server pricing, hold recovery after reload, payment confirmation, ticket retrieval, cancellation, and admin venue/seat/event creation and publication.
- Guest selection followed by registration resumed the booking correctly.
- Mobile navigation and document width were checked at 390px; desktop was checked at 1440px. No browser JavaScript errors were reported.
- Updated interface is served at http://localhost:8080/ with a three-event development catalog.
- Browser screenshots: target/ui-desktop.png, target/ui-mobile.png, target/ui-seats.png, target/ui-tickets.png, target/ui-admin.png.
